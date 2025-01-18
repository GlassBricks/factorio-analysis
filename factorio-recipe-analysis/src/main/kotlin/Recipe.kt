package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.QualityPrototype
import glassbricks.factorio.prototypes.RecipePrototype
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.IngredientVector
import glassbricks.recipeanalysis.vector
import glassbricks.recipeanalysis.vectorUnsafe

private inline fun IngredientVector.vectorMapKeys(transform: (Ingredient) -> Ingredient): IngredientVector =
    vectorUnsafe(this.mapKeys { transform(it.key) })

fun Ingredient.maybeWithQuality(quality: QualityPrototype): Ingredient = when (this) {
    is Item -> this.withQuality(quality)
    else -> this
}

fun IngredientVector.withItemsQuality(quality: QualityPrototype): IngredientVector =
    vectorMapKeys { it.maybeWithQuality(quality) }

class Recipe private constructor(
    val prototype: RecipePrototype,
    val quality: QualityPrototype,
    val baseIngredients: IngredientVector,
    val baseProducts: IngredientVector,
    val baseProductsIgnoredFromProductivity: IngredientVector,
) {
    val craftingTime: Double get() = prototype.energy_required
    val ingredients
        get() = if (quality.level.toInt() == 0) baseIngredients else baseIngredients.withItemsQuality(quality)
    val products get() = if (quality.level.toInt() == 0) baseProducts else baseProducts.withItemsQuality(quality)
    val productsIgnoredFromProductivity
        get() = if (quality.level.toInt() == 0) baseProductsIgnoredFromProductivity else baseProductsIgnoredFromProductivity.withItemsQuality(
            quality
        )

    fun withQuality(quality: QualityPrototype): Recipe = Recipe(
        prototype = prototype,
        quality = quality,
        baseIngredients = baseIngredients,
        baseProducts = baseProducts,
        baseProductsIgnoredFromProductivity = baseProductsIgnoredFromProductivity
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Recipe) return false

        if (prototype != other.prototype) return false
        if (quality != other.quality) return false
        return true
    }

    override fun hashCode(): Int {
        var result = prototype.hashCode()
        result = 31 * result + quality.hashCode()
        return result
    }

    companion object {
        fun fromPrototype(
            prototype: RecipePrototype,
            quality: QualityPrototype,
            map: IngredientsMap,
        ): Recipe {
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
                        minOf(productAmount.ignoredByProductivityAmount, productAmount.amount)
                }
            }
            return Recipe(
                prototype = prototype,
                quality = quality,
                baseIngredients = vector(ingredients),
                baseProducts = vector(products),
                baseProductsIgnoredFromProductivity = vector(ignoreFromProductivity)
            )
        }
    }
}
