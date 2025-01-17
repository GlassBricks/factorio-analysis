package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.RecipePrototype
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.IngredientVector
import glassbricks.recipeanalysis.vector

sealed interface AnyRecipe {
    val prototype: RecipePrototype
    val ingredients: IngredientVector
    val products: IngredientVector
    val time: Double
}

class Recipe private constructor(
    override val prototype: RecipePrototype,
    override val ingredients: IngredientVector,
    override val products: IngredientVector,
    val productsIgnoredFromProductivity: IngredientVector,
) : AnyRecipe {
    override val time: Double get() = prototype.energy_required

    companion object {
        fun fromPrototype(prototype: RecipePrototype, map: IngredientsMap): Recipe {
            val ingredients = buildMap {
                for (ingredient in prototype.ingredients.orEmpty()) {
                    val ingredientAmount = map.getIngredientAmount(ingredient)
                    put(ingredientAmount.ingredient, ingredientAmount.amount)
                }
            }
            val ignoreFromProductivity = mutableMapOf<Ingredient, Double>()
            val products = buildMap {
                for (product in prototype.results.orEmpty()) {
                    val productAmount = map.getProductAmount(product)
                    put(productAmount.ingredient, productAmount.amount)
                    ignoreFromProductivity[productAmount.ingredient] =
                        maxOf(productAmount.ignoredByProductivityAmount, productAmount.amount)
                }
            }
            return Recipe(prototype, vector(ingredients), vector(products), vector(ignoreFromProductivity))
        }
    }
}
