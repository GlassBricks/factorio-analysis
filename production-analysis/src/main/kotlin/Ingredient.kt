package glassbricks.recipeanalysis

interface Ingredient : Symbol

typealias IngredientRate = MapVector<Ingredient, Rate>
typealias IngredientVector = AmountVector<Ingredient>

interface LpProcess {
    val netRate: IngredientRate
    override fun toString(): String
}
