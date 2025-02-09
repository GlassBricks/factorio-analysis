package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.*
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap

data class ProductionLp(
    val inputs: List<Input>,
    val outputs: List<Output>,
    val processes: List<RealProcess>,
    val otherProcesses: List<PseudoProcess> = emptyList(),
    val constraints: List<SymbolConstraint> = emptyList(),
    val symbolConfigs: Map<Symbol, VariableConfig> = emptyMap(),
    val surplusCost: Double = 0.0,
) {

    val allProcesses by lazy {
        concat(inputs, outputs, processes, otherProcesses)
    }

    val inputsByIngredient by lazy { inputs.groupBy { it.ingredient } }
    val outputsByIngredient by lazy { outputs.groupBy { it.ingredient } }
    val processMap by lazy { processes.groupBy { it.process } }

    fun solve(solver: LpSolver = DefaultLpSolver(), options: LpOptions = LpOptions()): RecipeResult {
        val problem = createVarsAndConstraints()
        val result = solver.solve(problem.createSingleLp(), options)
        val solution = result.solution?.let { solution ->
            problem.createSolution(solution)
        }
        return RecipeResult(
            lpResult = result,
            solution = solution,
        )
    }

    override fun toString(): String = buildString {
        append("ProductionLp(")
        if (inputs.isNotEmpty()) {
            append(inputs.size)
            append(" inputs, ")
        }
        if (outputs.isNotEmpty()) {
            append(outputs.size)
            append(" outputs, ")
        }
        if (processes.isNotEmpty()) {
            append(processes.size)
            append(" processes, ")
        }
        if (otherProcesses.isNotEmpty()) {
            append(otherProcesses.size)
            append(" other processes, ")
        }
        if (constraints.isNotEmpty()) {
            append(constraints.size)
            append(" constraints, ")
        }
        if (symbolConfigs.isNotEmpty()) {
            append(symbolConfigs.size)
            append(" symbol configs, ")
        }
        append("surplusCost=", surplusCost)
        append(")")
    }
}

class ProductionLpVars(
    val processVariables: Map<PseudoProcess, Variable>,
    val surplusVariables: Map<Ingredient, Variable>,
    val symbolVariables: Map<Symbol, Variable>,
    val constraints: List<Constraint>,
    val objectiveWeights: Vector<Variable>,
) {
    fun createSingleLp(): LpProblem = LpProblem(
        constraints = constraints,
        objective = Objective(objectiveWeights, maximize = false),
    )

    internal fun createSolution(solution: LpSolution): RecipeSolution {
        fun <T> getAssignment(variables: Map<T, Variable>): Vector<T> =
            variables.mapValuesToVector { (_, variable) -> solution.assignment[variable] }
        return RecipeSolution(
            lpProcesses = getAssignment(processVariables),
            surpluses = getAssignment(surplusVariables),
            symbolUsage = getAssignment(symbolVariables),
            objectiveValue = solution.objectiveValue,
        )
    }
}

internal fun ProductionLp.createVarsAndConstraints(existingVars: Map<ProductionStage, ProductionLpVars> = emptyMap()): ProductionLpVars {
    val symbolVariables = mutableMapOf<Symbol, Variable>()
    val additionalConstraints = mutableListOf<Constraint>()
    val objectiveWeights = VectorBuilder<Variable>()

    fun VariableConfig.createVariable(name: String = ""): Variable = createVariableNoCost(name).also { variable ->
        objectiveWeights[variable] = cost
    }

    fun getSymbolVar(symbol: Symbol): Variable? {
        val existing = symbolVariables[symbol]
        if (existing != null) return existing
        if (symbol is ReferenceSymbol) {
            val stageVars = existingVars[symbol.stage]
                ?: error("A referenced stage ${symbol.stage} not found. Make sure all stages are specified in the problem.")
            return symbol.resolveVariable(stageVars).also { symbolVariables[symbol] = it }
        }
        return null
    }

    val processVariables = allProcesses.associateWith { process ->
        process.variableConfig.createVariable().also { variable ->
            val symbol = process.symbol
            if (symbol != null) {
                val existingVar = getSymbolVar(symbol)
                require(existingVar == null) {
                    "Symbol $symbol is used in multiple processes. " +
                            "Use an equality constraint instead if you want 2 recipes to have the same usage."
                }
                symbolVariables[symbol] = variable
            }

        }
    }
    val processCostVariables = processVariables.mapValues { (process, processVar) ->
        val costVariableConfig = process.costVariableConfig
        costVariableConfig?.createVariable("Cost of $process")?.also { costVariable ->
            // cost - recipe >= 0
            additionalConstraints.add(
                Constraint(
                    lhs = vectorOf(costVariable to 1.0, processVar to -1.0),
                    rhs = 0.0,
                    op = ComparisonOp.Geq
                )
            )
        } ?: processVar
    }

    for ((symbol, config) in symbolConfigs) {
        val existingVar = getSymbolVar(symbol)
        require(existingVar == null) { "Symbol $symbol specified in symbolConfigs is already configured somewhere else." }
        symbolVariables[symbol] = config.createVariable("Symbol $symbol")
    }

    // surplus_j = sum ( recipe_i * recipe_rate_ij )
    val (recipeEquations, surplusVariables) = createMatrixEquations(
        processVariables,
        { it.ingredientRate },
        { item ->
            Variable(name = "surplus $item", lowerBound = 0.0)
                .also {
                    objectiveWeights[it] = surplusCost
                }
        },
    )

    fun getOrCreateSymbolVar(symbol: Symbol): Variable =
        getSymbolVar(symbol) ?: Variable(name = "Symbol $symbol", lowerBound = 0.0).also {
            symbolVariables[symbol] = it
        }
    // cost_j = sum ( recipe_i * recipe_cost_ij )
    val (costEquations) = createMatrixEquations(
        processCostVariables,
        { it.additionalCosts },
        { symbol -> getOrCreateSymbolVar(symbol) },
    )

    for (constraint in constraints) {
        additionalConstraints += constraint.mapKeys { getOrCreateSymbolVar(it) }
    }
    return ProductionLpVars(
        processVariables,
        surplusVariables,
        symbolVariables,
        constraints = concat(recipeEquations, costEquations, additionalConstraints),
        objectiveWeights = objectiveWeights.build(),
    )
}

/**
 * Creates variables and constraints such that:
 * ```
 * for all weight keys k:
 *    var(k) = sum( var(recipe) * recipe.weight[r] )
 * ```
 */
private inline fun <R, K> createMatrixEquations(
    vars: Map<R, Variable>,
    weight: (R) -> AnyVector<K, *>,
    crossinline createVariable: (K) -> Variable,
): Pair<List<Constraint>, Map<K, Variable>> {
    // performance hotspot!
    val variables = Object2ObjectLinkedOpenHashMap<K, Variable>(2_000)
    val keyToCoeffs = Object2ObjectLinkedOpenHashMap<K, VectorBuilder<Variable>>(2_000)
    for ((row, rowVar) in vars) {
        for ((key, coeff) in weight(row)) {
            keyToCoeffs.getOrPut(key) {
                val newVariable = createVariable(key)
                VectorBuilder<Variable>().apply { this[newVariable] = -1.0 }
            }[rowVar] = coeff
        }
    }

    val constraints = keyToCoeffs.map { (_, coeffs) ->
        Constraint(lhs = coeffs.build(), rhs = 0.0, op = ComparisonOp.Eq)
    }

    return constraints to variables
}
