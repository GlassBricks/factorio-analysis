package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.*

data class ProductionLp(
    val processes: List<PseudoProcess>,
    val constraints: List<SymbolConstraint> = emptyList(),
    val symbolConfigs: Map<Symbol, VariableConfig> = emptyMap(),
    val surplusCost: Double = 0.0,
) {

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
}

class ProductionLpVars(
    val processVariables: Map<PseudoProcess, Variable>,
    val surplusVariables: Map<Ingredient, Variable>,
    val symbolVariables: Map<Symbol, Variable>,
    val constraints: List<Constraint>,
    val objectiveWeights: Map<Variable, Double>,
) {
    fun createSingleLp(): LpProblem = LpProblem(
        constraints = constraints,
        objective = Objective(objectiveWeights, maximize = false),
    )

    internal fun createSolution(solution: LpSolution): RecipeSolution {
        fun <T> getAssignment(variables: Map<T, Variable>): Vector<T> =
            vector(variables.mapValues { (_, variable) -> solution.assignment[variable] })
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
    val objectiveWeights = mutableMapOf<Variable, Double>()

    fun VariableConfig.createVariable(name: String): Variable = createVariableNoCost(name).also { variable ->
        objectiveWeights[variable] = cost
    }

    fun getSymbolVar(symbol: Symbol): Variable? {
        val existing = symbolVariables[symbol]
        if (existing != null) return existing
        if (symbol is ReferenceSymbol) {
            val stageVars = existingVars[symbol.stage]
                ?: error("A referenced stage ${symbol.stage} not found. Make sure all stages are passed in together.")
            return symbol.resolveVariable(stageVars).also { symbolVariables[symbol] = it }
        }
        return null
    }

    val processVariables = processes.associateWith { process ->
        process.variableConfig.createVariable("Process $process").also { variable ->
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
                    lhs = mapOf(costVariable to 1.0, processVar to -1.0),
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
        objectiveWeights = objectiveWeights,
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
    recipeVars: Map<R, Variable>,
    weight: (R) -> MapVector<K, *>,
    createVariable: (K) -> Variable,
    op: ComparisonOp = ComparisonOp.Eq,
): Pair<List<Constraint>, Map<K, Variable>> {
    val elementsByKeys = recipeVars.entries.groupByMulti { weight(it.key).keys }
    val allKeys = elementsByKeys.keys
    val keyToVar = LinkedHashMap<K, Variable>(allKeys.size)
    val constraints = mutableListOf<Constraint>()
    for ((key, entries) in elementsByKeys) {
        val keyVar = createVariable(key)
        keyToVar[key] = keyVar
        val coeffs = buildMap {
            for ((element, elementVar) in entries) {
                this[elementVar] = weight(element)[key]
            }
            this[keyVar] = -1.0
        }
        constraints.add(Constraint(coeffs, op, 0.0))
    }
    return constraints to keyToVar
}

private inline fun <T, K> Iterable<T>.groupByMulti(getKeys: (T) -> Iterable<K>): Map<K, List<T>> =
    buildMap<K, MutableList<T>> {
        for (element in this@groupByMulti) {
            for (ingredient in getKeys(element)) {
                this.getOrPut(ingredient, ::mutableListOf).add(element)
            }
        }
    }
