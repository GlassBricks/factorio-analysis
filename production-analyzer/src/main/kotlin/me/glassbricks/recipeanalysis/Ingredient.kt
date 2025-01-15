package me.glassbricks.recipeanalysis

interface IngredientCategory : Symbol

interface Ingredient : Symbol {
    val category: IngredientCategory
}

typealias Ingredients = MapVector<Ingredient, Unit>
typealias IngredientRate = MapVector<Ingredient, Rate>


interface RecipeCategory : Symbol

interface RecipeRate  {
    val inputRate: IngredientRate
    val outputRate: IngredientRate
}
interface RecipeResolved : Symbol, RecipeRate {
    val category: RecipeCategory
}
