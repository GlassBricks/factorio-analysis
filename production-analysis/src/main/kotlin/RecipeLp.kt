/**
 * Given:
 * - Possible recipes to use, with cost
 * - Fixed "free" inputs
 * - Inputs with weighted cost
 * - Outputs required
 * - Outputs with weighted objective cost
 * - Other custom constraints on recipes used
 *
 * Finds the optimal recipe usage to, in the following priorities
 * - Meets all constraints
 */
package glassbricks.recipeanalysis

interface PseudoProcess {
    val lowerBound: Double get() = 0.0
    val upperBound: Double
    val cost: Double
    val integral: Boolean
    val ingredientRate: IngredientRate
}

data class Process(
    val process: LpProcess,
    override val cost: Double = 1.0,
    override val upperBound: Double = Double.POSITIVE_INFINITY,
    override val integral: Boolean = false,
) : PseudoProcess {
    override val ingredientRate: IngredientRate get() = process.netRate

    override fun toString(): String = buildString {
        append("Process(")
        append(process)
        if (cost != 1.0) append(", cost=").append("%e".format(cost))
        if (upperBound != Double.POSITIVE_INFINITY) append(", upperBound=").append("%e".format(upperBound))
        if (integral) append(", integral=true")
        append(")")
    }
}

data class Input(
    val ingredient: Ingredient,
    override val cost: Double,
    override val upperBound: Double = Double.POSITIVE_INFINITY,
) : PseudoProcess {
    override val integral: Boolean get() = false
    override val ingredientRate: IngredientRate get() = vector(ingredient to 1.0)
    override fun toString(): String = buildString {
        append("Input(")
        append(ingredient)
        if (cost != 1.0) append(", cost=").append("%e".format(cost))
        if (upperBound != Double.POSITIVE_INFINITY) append(", upperBound=").append("%e".format(upperBound))
        append(")")
    }
}

data class Output(
    val ingredient: Ingredient,
    val weight: Double,
    override val lowerBound: Double,
) : PseudoProcess {
    override val upperBound: Double get() = Double.POSITIVE_INFINITY
    override val cost get() = -weight
    override val integral: Boolean get() = false
    override val ingredientRate: IngredientRate get() = vector(ingredient to -1.0)

    override fun toString(): String = buildString {
        append("Output(")
        append(ingredient)
        if (weight != 1.0) append(", weight=").append("%e".format(weight))
        if (lowerBound != 0.0) append(", lowerBound=").append("%e".format(lowerBound))
        append(")")
    }
}

data class RecipeLp(
    val processes: List<PseudoProcess>,
    val surplusCost: Double = 1e-5,
    val lpOptions: LpOptions = LpOptions(),
)

data class RecipeLpSolution(
    val lpResult: LpResult,
    val recipeUsage: AmountVector<PseudoProcess>?,
)

fun RecipeLp.solve(): RecipeLpSolution {
    val recipeVariables = processes.associateWith { recipe ->
        Variable(name = recipe.toString(), lb = recipe.lowerBound, ub = recipe.upperBound, integral = recipe.integral)
    }
    val recipesByItem = buildRecipesByItem(processes)
    val allItems = recipesByItem.keys

    val surplusRecipeVariables = allItems.associateWith { item ->
        Variable(name = item.toString(), lb = 0.0, ub = Double.POSITIVE_INFINITY, integral = false)
    }

    val constraints = recipesByItem.map { (item, recipes) ->
        // production/consumption of all recipes
        val coeffs = buildMap {
            for (recipe in recipes) {
                this[recipeVariables[recipe]!!] = recipe.ingredientRate[item]
            }
            this[surplusRecipeVariables[item]!!] = -1.0
        }
        coeffs eq 0.0
    }

    val objective = Objective(
        coefficients = buildMap {
            for ((recipe, variable) in recipeVariables) {
                this[variable] = recipe.cost
            }
            for (variable in surplusRecipeVariables.values) {
                this[variable] = surplusCost
            }
        },
        maximize = false
    )

    val problem = LpProblem(constraints = constraints, objective = objective)
    val solver = DefaultLpSolver()
    val result = solver.solveLp(problem, options = lpOptions)

    val assignment = result.solution?.assignment
    val recipeUsage = if (assignment == null) null else {
        amountVector(recipeVariables.mapValuesNotNull { (_, variable) -> assignment[variable] })
    }

    return RecipeLpSolution(lpResult = result, recipeUsage = recipeUsage)
}

private fun buildRecipesByItem(recipes: List<PseudoProcess>): Map<Ingredient, List<PseudoProcess>> =
    buildMap<_, MutableList<PseudoProcess>> {
        for (recipe in recipes) {
            for ((ingredient, _) in recipe.ingredientRate) {
                this.getOrPut(ingredient, ::mutableListOf).add(recipe)
            }
        }
    }
