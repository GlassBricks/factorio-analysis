package glassbricks.recipeanalysis

class CustomProcessBuilder(val name: String) {
    var ingredientRate: IngredientRate = emptyVector()
    var additionalCosts: Vector<Symbol> = emptyVector()
    var lowerBound: Double = 0.0
    var upperBound: Double = Double.POSITIVE_INFINITY
    var cost: Double = 0.0
    var integral: Boolean = false
    var symbol: Symbol? = null
    fun build(): CustomProcess = CustomProcess(
        name = name,
        symbol = symbol,
        ingredientRate = ingredientRate,
        additionalCosts = additionalCosts,
        lowerBound = lowerBound,
        upperBound = upperBound,
        cost = cost,
        integral = integral
    )
}
