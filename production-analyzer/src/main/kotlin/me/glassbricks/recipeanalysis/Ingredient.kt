package me.glassbricks.recipeanalysis

interface Ingredient : Symbol

typealias IngredientRate = MapVector<Ingredient, Rate>
typealias IngredientVector = AmountVector<Ingredient>

interface RecipeRate  {
    val netRate: IngredientRate
    override fun toString(): String
}
