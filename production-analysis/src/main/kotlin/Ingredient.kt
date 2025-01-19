package glassbricks.recipeanalysis

interface Ingredient : Symbol

typealias IngredientRate = MapVector<Ingredient, Rate>
typealias IngredientVector = AmountVector<Ingredient>

interface Process {
    val netRate: IngredientRate
    override fun toString(): String
}
