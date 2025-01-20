package glassbricks.recipeanalysis

interface Ingredient : Symbol

data class StrIngredient(val name: String) : Ingredient {
    override fun toString(): String = "Ingredient($name)"
}

fun Ingredient(name: String): StrIngredient = StrIngredient(name)

typealias IngredientRate = MapVector<Ingredient, Rate>
typealias IngredientVector = AmountVector<Ingredient>

interface Process {
    val netRate: IngredientRate
    override fun toString(): String
}
