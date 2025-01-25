package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.*

/**
 * Can be:
 * - [Input], in absolute amounts (given) or with cost per unit (minimize)
 * - [Output], in absolute amounts (required) or with weight per unit (maximize)
 * - An actual [LpProcess] that turns inputs into outputs, possibly with constraints
 */
interface PseudoProcess {
    val ingredientRate: IngredientRate
    val variableConfig: VariableConfig

    val additionalCosts: Vector<Symbol> get() = emptyVector()

    /**
     * If set, a second variable >= the recipe's variable will be created to represent the cost.
     *
     * Additional costs will be associated with this variable, not the recipe's variable.
     */
    val costVariableConfig: VariableConfig?
    val symbol: Symbol?
}

private fun StringBuilder.commonToString(process: PseudoProcess) {
    if (process.variableConfig != VariableConfig()) append(", variableConfig=").append(process.variableConfig)
    if (process.additionalCosts.isNotEmpty()) append(", additionalCosts=").append(process.additionalCosts)
    if (process.costVariableConfig != null) append(", costVariableConfig=").append(process.costVariableConfig)
    if (process.symbol != null) append(", symbol=").append(process.symbol)
}

data class LpProcess(
    val process: Process,
    override val variableConfig: VariableConfig = VariableConfig(),
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val costVariableConfig: VariableConfig? = null,
    override val symbol: Symbol? = null,
) : PseudoProcess {
    override val ingredientRate: IngredientRate get() = process.netRate

    override fun toString(): String = buildString {
        append("LpProcess(")
        append(process)
        commonToString(this@LpProcess)
        append(")")
    }
}

data class Input(
    val ingredient: Ingredient,
    override val variableConfig: VariableConfig,
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val costVariableConfig: VariableConfig? = null,
    override val symbol: Symbol? = null,
) : PseudoProcess {
    override val ingredientRate: IngredientRate get() = vectorWithUnits(ingredient to 1.0)
    override fun toString(): String = buildString {
        append("Input(")
        append(ingredient)
        commonToString(this@Input)
        append(")")
    }
}

data class Output(
    val ingredient: Ingredient,
    override val variableConfig: VariableConfig,
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val costVariableConfig: VariableConfig? = null,
    override val symbol: Symbol? = null,
) : PseudoProcess {
    init {
        require(variableConfig.cost <= 0.0) { "Output cost must be negative (to optimize for!)" }
    }

    override val ingredientRate: IngredientRate get() = vectorWithUnits(ingredient to -1.0)

    override fun toString(): String = buildString {
        append("Output(")
        append(ingredient)
        commonToString(this@Output)
        append(")")
    }
}

data class CustomProcess(
    val name: String,
    override val ingredientRate: IngredientRate,
    override val variableConfig: VariableConfig,
    override val costVariableConfig: VariableConfig? = null,
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val symbol: Symbol? = null,
) : PseudoProcess {
    override fun toString(): String = buildString {
        append("CustomProcess(")
        append(name)
        commonToString(this@CustomProcess)
        append(")")
    }
}

class CustomProcessBuilder(val name: String) {
    var ingredientRate: IngredientRate = emptyVector()
    var additionalCosts: Vector<Symbol> = emptyVector()
    val variableConfig = VariableConfigBuilder()
    var symbol: Symbol? = null
    fun build(): CustomProcess = CustomProcess(
        name = name,
        symbol = symbol,
        ingredientRate = ingredientRate,
        additionalCosts = additionalCosts,
        variableConfig = variableConfig.build(),
    )
}

data class RecipeLp(
    val processes: List<PseudoProcess>,
    val constraints: List<SymbolConstraint> = emptyList(),
    val symbolConfigs: Map<Symbol, VariableConfig> = emptyMap(),
    val surplusCost: Double = 1e-5,
    val lpSolver: LpSolver = DefaultLpSolver(),
    val lpOptions: LpOptions = LpOptions(),
)

fun RecipeLp.solve(): RecipeLpResult {
    val problem = createAsLp()
    val result = lpSolver.solve(problem.lp, lpOptions)
    return problem.createResult(result)
}

fun RecipeLp.createIncrementalSolver(): IncrementalSolver<RecipeLpResult> {
    val asLp = createAsLp()
    val solver = lpSolver.createIncrementalSolver(asLp.lp, lpOptions)
    return solver.map { asLp.createResult(it) }
}

private class RecipeAsLp(
    val lp: LpProblem,
    val processVariables: Map<PseudoProcess, Variable>,
    val surplusVariables: Map<Ingredient, Variable>,
    val symbolVariables: Map<Symbol, Variable>,
) {
    fun createResult(result: LpResult): RecipeLpResult {
        val solution = result.solution?.let { solution ->
            fun <T> getAssignment(variables: Map<T, Variable>): Vector<T> =
                vector(variables.mapValuesNotNull { (_, variable) -> solution.assignment[variable] })
            RecipeLpSolution(
                recipeUsage = getAssignment(processVariables),
                surpluses = getAssignment(surplusVariables),
                symbolUsage = getAssignment(symbolVariables),
            )
        }
        return RecipeLpResult(
            lpResult = result,
            solution = solution,
        )
    }
}

/**
 * Default lower bound for un-configured symbols is 0.0
 */
private fun RecipeLp.createAsLp(): RecipeAsLp {
    val symbolVariables = mutableMapOf<Symbol, Variable>()
    val additionalConstraints = mutableListOf<Constraint>()
    val objectiveWeights = mutableMapOf<Variable, Double>()

    fun VariableConfig.createVariable(name: String): Variable = createVariableNoCost(name).also { variable ->
        objectiveWeights[variable] = cost
    }

    val processVariables = processes.associateWith { process ->
        process.variableConfig.createVariable("Process $process").also { variable ->
            val symbol = process.symbol
            if (symbol != null) {
                require(symbol !in symbolVariables) {
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
        require(symbol !in symbolVariables) { "Symbol $symbol is already used in a process" }
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

    fun getOrCreateVariable(symbol: Symbol): Variable = symbolVariables.getOrPut(symbol) {
        Variable(name = "Symbol $symbol", lowerBound = 0.0)
    }
    // cost_j = sum ( recipe_i * recipe_cost_ij )
    val (costEquations) = createMatrixEquations(
        processCostVariables,
        { it.additionalCosts },
        { symbol -> getOrCreateVariable(symbol) },
    )

    constraints.forEach { constraint ->
        additionalConstraints += constraint.mapKeys { getOrCreateVariable(it) }
    }

    val objective = Objective(
        coefficients = objectiveWeights,
        maximize = false
    )

    val lp = LpProblem(
        constraints = concat(recipeEquations, costEquations, additionalConstraints),
        objective = objective
    )
    return RecipeAsLp(lp, processVariables, surplusVariables, symbolVariables)

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
