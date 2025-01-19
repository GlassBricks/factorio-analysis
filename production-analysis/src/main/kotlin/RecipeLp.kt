package glassbricks.recipeanalysis

/**
 * Can be:
 * - [Input], in absolute amounts (given) or with cost per unit (minimize)
 * - [Output], in absolute amounts (required) or with weight per unit (maximize)
 * - An actual [LpProcess] that turns inputs into outputs, possibly with constraints
 */
interface PseudoProcess {
    val lowerBound: Double get() = 0.0
    val upperBound: Double
    val cost: Double
    val integral: Boolean
    val ingredientRate: IngredientRate
    val additionalCosts: AmountVector<Symbol> get() = emptyVector()
}

private fun StringBuilder.commonToString(
    process: PseudoProcess,
    defaultCost: Double = Double.NaN,
) {
    if (process.cost != defaultCost) append(", cost=").append("%e".format(process.cost))
    if (process.upperBound != Double.POSITIVE_INFINITY) append(", upperBound=").append("%e".format(process.upperBound))
    if (process.integral) append(", integral=true")
    if (process.additionalCosts.isNotEmpty()) append(", additionalCosts=").append(process.additionalCosts)
}

data class LpProcess(
    val process: Process,
    override val cost: Double = 1.0,
    override val upperBound: Double = Double.POSITIVE_INFINITY,
    override val integral: Boolean = false,
    override val additionalCosts: AmountVector<Symbol> = emptyVector(),
) : PseudoProcess {
    override val ingredientRate: IngredientRate get() = process.netRate

    override fun toString(): String = buildString {
        append("LpProcess(")
        append(process)
        commonToString(this@LpProcess, 1.0)
        append(")")
    }

}

data class Input(
    val ingredient: Ingredient,
    override val cost: Double,
    override val upperBound: Double = Double.POSITIVE_INFINITY,
    override val integral: Boolean = false,
    override val additionalCosts: AmountVector<Symbol> = emptyVector(),
) : PseudoProcess {
    override val ingredientRate: IngredientRate get() = vector(ingredient to 1.0)
    override fun toString(): String = buildString {
        append("Input(")
        append(ingredient)
        commonToString(this@Input)
        append(")")
    }
}

data class Output(
    val ingredient: Ingredient,
    val weight: Double,
    override val lowerBound: Double,
    override val integral: Boolean = false,
    override val additionalCosts: AmountVector<Symbol> = emptyVector(),
) : PseudoProcess {
    override val upperBound: Double get() = Double.POSITIVE_INFINITY
    override val cost get() = -weight
    override val ingredientRate: IngredientRate get() = vector(ingredient to -1.0)

    override fun toString(): String = buildString {
        append("Output(")
        append(ingredient)
        if (weight != 1.0) append(", weight=").append("%e".format(weight))
        if (lowerBound != 0.0) append(", lowerBound=").append("%e".format(lowerBound))
        if (integral) append(", integral=true")
        if (additionalCosts.isNotEmpty()) append(", additionalCosts=").append(additionalCosts)
        append(")")
    }
}

data class RecipeLp(
    val processes: List<PseudoProcess>,
    val additionalConstraints: List<SymbolConstraint> = emptyList(),
    val surplusCost: Double = 1e-5,
    val symbolCosts: Map<Symbol, Double> = emptyMap(),
    val lpOptions: LpOptions = LpOptions(),
)

private inline fun <T> StringBuilder.displayLeftRight(
    list: List<T>,
    left: (T) -> Any,
    right: (T) -> Double,
) = apply {
    val lefts = list.map { left(it).toString() }
    val leftWidth = lefts.maxOfOrNull { it.length } ?: 0
    for ((el, left) in list.zip(lefts)) {
        append(left)
        repeat(leftWidth - left.length) { append(' ') }
        append(": ")
        append("%10.5f".format(right(el)))
        append('\n')
    }
}

data class RecipeLpSolution(
    val recipes: AmountVector<PseudoProcess>,
    val surpluses: AmountVector<Ingredient>,
    val additionalCosts: AmountVector<Symbol>,
) {
    fun display() = buildString {
        appendLine("Inputs:")
        val inputs = recipes.keys.filterIsInstance<Input>()
        displayLeftRight(inputs, { it.ingredient }) { recipes[it] }
        appendLine()

        appendLine("Outputs:")
        val outputs = recipes.keys.filterIsInstance<Output>()
        displayLeftRight(outputs, { it.ingredient }) { recipes[it] }
        appendLine()

        appendLine("Recipes:")
        val processes = recipes.keys.filterIsInstance<LpProcess>()
        displayLeftRight(processes, { it.process }) { recipes[it] }
        appendLine()

        val otherProcesses = recipes.keys.filter {
            it !is Input && it !is Output && it !is LpProcess
        }
        if (otherProcesses.isNotEmpty()) {
            appendLine()
            appendLine("Other processes:")
            displayLeftRight(otherProcesses, { it }) { recipes[it] }
        }

        if (surpluses.isNotEmpty()) {
            appendLine()
            appendLine("Surpluses:")
            displayLeftRight(surpluses.keys.toList(), { it }) { surpluses[it] }
        }
    }

}

data class RecipeLpResult(
    val lpResult: LpResult,
    val solution: RecipeLpSolution?,
) {
    val lpSolution: LpSolution? get() = lpResult.solution
    val status: LpResultStatus get() = lpResult.status
}

fun RecipeLp.solve(): RecipeLpResult {
    val recipeVariables = processes.associateWith { recipe ->
        Variable(name = recipe.toString(), lb = recipe.lowerBound, ub = recipe.upperBound, integral = recipe.integral)
    }
    // surplus_j = sum ( recipe_i * recipe_rate_ij )
    val (recipeEquations, surplusVariables) = createEquations(
        recipeVariables,
        { it.ingredientRate },
        { item -> Variable(name = "surplus $item", lb = 0.0) },
    )
    // cost_j = sum ( recipe_i * recipe_cost_ij )
    val (costEquations, costVariables) = createEquations(
        recipeVariables,
        { it.additionalCosts },
        { symbol -> Variable(name = "cost $symbol") },
    )

    val allSymbolVars = buildSet {
        for (constraint in additionalConstraints) {
            addAll(constraint.lhs.keys)
        }
        addAll(symbolCosts.keys)
    }
        .associateWith { costVariables[it] ?: Variable(name = "symbol $it") }
    val additionalConstraints = additionalConstraints.map { (lhs, op, rhs) ->
        Constraint(lhs.mapKeys { allSymbolVars[it.key]!! }, op, rhs)
    }

    val objective = Objective(
        coefficients = buildMap {
            for ((recipe, variable) in recipeVariables) {
                this[variable] = recipe.cost
            }
            for (variable in surplusVariables.values) {
                this[variable] = surplusCost
            }
            for ((symbol, variable) in allSymbolVars) {
                symbolCosts[symbol]?.let {
                    this[variable] = it
                }
            }
        },
        maximize = false
    )

    val lp = LpProblem(
        constraints = concat(recipeEquations, costEquations, additionalConstraints),
        objective = objective
    )
    val result = lp.solve(lpOptions)

    val solution = result.solution?.let { solution ->
        fun <T> getAssignment(variables: Map<T, Variable>): AmountVector<T> =
            amountVector(variables.mapValuesNotNull { (_, variable) -> solution.assignment[variable] })
        RecipeLpSolution(
            recipes = getAssignment(recipeVariables),
            surpluses = getAssignment(surplusVariables),
            additionalCosts = getAssignment(allSymbolVars),
        )
    }

    return RecipeLpResult(
        lpResult = result,
        solution = solution,
    )
}

/**
 * Creates variables and constraints such that:
 * ```
 * for all keys K:
 *    var_k = sum( recipe.var * recipe.weight[K] )
 * ```
 * where `recipe.var` is from [recipeVars], recipe.weight[K] is from [weight],
 * and `var_k` are new variables created by [createVariable].
 */
private inline fun <R, K> createEquations(
    recipeVars: Map<R, Variable>,
    weight: (R) -> MapVector<K, *>,
    createVariable: (K) -> Variable,
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
        constraints.add(coeffs eq 0.0)
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
