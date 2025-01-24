package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.IngredientVector
import glassbricks.recipeanalysis.vectorWithUnits

/**
 * All items should have default quality
 */
interface IngredientsMap {
    val defaultQuality: Quality
    fun get(id: ItemID): Item
    fun get(id: FluidID): Fluid
}

data class IngredientAmount(
    val ingredient: Ingredient, val amount: Double,
    val ignoredByProductivityAmount: Double = 0.0,
)

fun IngredientsMap.getIngredientAmount(ingredient: IngredientPrototype): IngredientAmount = when (ingredient) {
    is ItemIngredientPrototype -> IngredientAmount(get(ingredient.name), ingredient.amount.toDouble())
    is FluidIngredientPrototype -> IngredientAmount(get(ingredient.name), ingredient.amount)
}

fun IngredientsMap.getProductAmount(product: ProductPrototype): IngredientAmount = when (product) {
    is ItemProductPrototype -> {
        val item = get(product.name)
        val baseAmount: Double = product.amount ?: ((product.amount_min!! + product.amount_max!!).toDouble() / 2.0)
        // ignore extraCountFraction???
        val amount: Double = baseAmount * product.probability
        IngredientAmount(item, amount, product.ignored_by_productivity?.toDouble() ?: 0.0)
    }

    is FluidProductPrototype -> {
        val fluid = get(product.name)
        val baseAmount = product.amount ?: ((product.amount_min!! + product.amount_max!!) / 2.0)
        val amount = baseAmount * product.probability
        IngredientAmount(fluid, amount, product.ignored_by_productivity ?: 0.0)
    }

    is ResearchProgressProductPrototype -> TODO("research progress as product")
}

fun IngredientsMap.getProductsVector(prototypes: List<ProductPrototype>?): Pair<IngredientVector, IngredientVector> {
    val ignoreFromProductivity = mutableMapOf<Ingredient, Double>()
    val products = buildMap {
        for (product in prototypes.orEmpty()) {
            val productAmount = this@getProductsVector.getProductAmount(product)
            put(productAmount.ingredient, productAmount.amount)
            ignoreFromProductivity[productAmount.ingredient] =
                minOf(productAmount.ignoredByProductivityAmount, productAmount.amount)
        }
    }
    return Pair(vectorWithUnits(products), vectorWithUnits(ignoreFromProductivity))
}
