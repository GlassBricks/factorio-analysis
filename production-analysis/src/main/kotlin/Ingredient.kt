package glassbricks.recipeanalysis

interface Ingredient : Symbol

typealias IngredientRate = MapVector<Ingredient, Rate>
typealias IngredientVector = AmountVector<Ingredient>

interface CraftingProcess {
    val netRate: IngredientRate
    override fun toString(): String
}
