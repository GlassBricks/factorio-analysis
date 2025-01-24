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
    val additionalCosts: Vector<Symbol> get() = emptyVector()
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
    override val additionalCosts: Vector<Symbol> = emptyVector(),
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
    override val additionalCosts: Vector<Symbol> = emptyVector(),
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
    val weight: Double,
    override val lowerBound: Double,
    override val integral: Boolean = false,
    override val additionalCosts: Vector<Symbol> = emptyVector(),
) : PseudoProcess {
    override val upperBound: Double get() = Double.POSITIVE_INFINITY
    override val cost get() = -weight
    override val ingredientRate: IngredientRate get() = vectorWithUnits(ingredient to -1.0)

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

data class CustomProcess(
    val name: String,
    override val ingredientRate: IngredientRate,
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val lowerBound: Double = 0.0,
    override val upperBound: Double = Double.POSITIVE_INFINITY,
    override val cost: Double = 0.0,
    override val integral: Boolean = false,
) : PseudoProcess {
    override fun toString(): String = "CustomProcess($name)"
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
        append(" | ")
        appendLine("%10.5f".format(right(el)))
    }
}

data class RecipeLpSolution(
    val recipes: Vector<PseudoProcess>,
    val surpluses: Vector<Ingredient>,
    val additionalCosts: Vector<Symbol>,
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
        Variable(
            name = recipe.toString(),
            lowerBound = recipe.lowerBound,
            upperBound = recipe.upperBound,
            integral = recipe.integral
        )
    }
    // surplus_j = sum ( recipe_i * recipe_rate_ij )
    val (recipeEquations, surplusVariables) = createMatrixEquations(
        recipeVariables,
        { it.ingredientRate },
        { item -> Variable(name = "surplus $item", lowerBound = 0.0) },
    )
    // cost_j = sum ( recipe_i * recipe_cost_ij )
    val (costEquations, costVariables) = createMatrixEquations(
        recipeVariables,
        { it.additionalCosts },
        { symbol -> Variable(name = "cost $symbol") },
    )

    val allSymbolVars = buildSet {
        for (constraint in additionalConstraints) {
            addAll(constraint.lhs.keys)
        }
        addAll(symbolCosts.keys)
    }.associateWith {
        costVariables[it] ?: Variable(name = "symbol $it", lowerBound = 0.0)
    }
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
        fun <T> getAssignment(variables: Map<T, Variable>): Vector<T> =
            vector(variables.mapValuesNotNull { (_, variable) -> solution.assignment[variable] })
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
