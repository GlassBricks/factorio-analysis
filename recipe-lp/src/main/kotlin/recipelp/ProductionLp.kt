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

    //    val inputsByIngredient by lazy { inputs.groupBy { it.ingredient } }
    val outputsByIngredient by lazy { outputs.groupBy { it.ingredient } }
//    val processMap by lazy { processes.groupBy { it.process } }

    fun solve(solver: LpSolver = DefaultLpSolver(), options: LpOptions = LpOptions()): RecipeResult {
        val vars = createVarsAndConstraints(solver)
        val result = solver.solve(options)
        val solution = result.solution?.let { vars.createSolution(it) }
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
) {
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

internal fun ProductionLp.createVarsAndConstraints(
    solver: LpSolver,
    existingVars: Map<ProductionStage, ProductionLpVars> = emptyMap(),
): ProductionLpVars {
    val symbolVariables = mutableMapOf<Symbol, Variable>()

    fun getExistingVar(symbol: Symbol): Variable? = symbolVariables[symbol]
        ?: maybeResolveReferenceVar(symbol, existingVars)?.also { symbolVariables[symbol] = it }

    val processVariables = allProcesses.associateWith { process ->
        solver.addVariable(process.variableConfig)
    }
    val processCostVariables = processVariables.mapValues { (process, processVar) ->
        process.costVariableConfig
            ?.let { solver.addVariable(it) }
            ?.also { costVar ->
                solver.addConstraint(vectorOf(costVar to 1.0, processVar to -1.0) geq 0.0)
            }
            ?: processVar
    }

    for ((symbol, config) in symbolConfigs) {
        val existingVar = getExistingVar(symbol)
        require(existingVar == null) { "Symbol $symbol specified in symbolConfigs is already configured somewhere else." }
        symbolVariables[symbol] = solver.addVariable(config, "Symbol $symbol")
    }

    // surplus_j = sum ( recipe_i * recipe_rate_ij )
    val surplusVariables = solver.createMatrixEquations(
        processVariables,
        { it.ingredientRate },
    ) { ingredient -> solver.addPositiveVariable(cost = surplusCost) }

    fun getOrCreateVar(symbol: Symbol): Variable =
        getExistingVar(symbol) ?: solver.addPositiveVariable().also { symbolVariables[symbol] = it }
    // cost_j = sum ( recipe_i * recipe_cost_ij )
    solver.createMatrixEquations(
        processCostVariables,
        { it.additionalCosts },
    ) { symbol -> getOrCreateVar(symbol) }

    for (constraint in constraints) {
        solver.addConstraint(constraint.mapKeys { getOrCreateVar(it) })
    }
    return ProductionLpVars(
        processVariables,
        surplusVariables,
        symbolVariables,
    )
}

/**
 * Creates variables and constraints such that:
 * ```
 * for all weight keys k:
 *    var(k) = sum( var(recipe) * recipe.weight[r] )
 * ```
 */
private inline fun <R, K> LpSolver.createMatrixEquations(
    vars: Map<R, Variable>,
    weight: (R) -> AnyVector<K, *>,
    crossinline createVariable: (K) -> Variable,
): Map<K, Variable> = buildMap {
    val keyToCoeffs: MutableMap<K, Constraint> = Object2ObjectLinkedOpenHashMap(2_000)
    for ((row, rowVar) in vars) {
        for ((key, coeff) in weight(row)) {
            val constraint = keyToCoeffs.getOrPut(key) {
                val keyVar = createVariable(key)
                this@buildMap[key] = keyVar
                this@createMatrixEquations.addConstraint(lb = 0.0, ub = 0.0).also {
                    it[keyVar] = -1.0
                }
            }
            constraint[rowVar] = coeff
        }
    }
}
