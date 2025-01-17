package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import glassbricks.recipeanalysis.Ingredient

interface IngredientsMap {
    val ingredients: Map<String, Ingredient>
}

data class IngredientAmount(
    val ingredient: Ingredient, val amount: Double,
    val ignoredByProductivityAmount: Double = 0.0,
)

fun IngredientsMap.getIngredientAmount(ingredient: IngredientPrototype): IngredientAmount = when (ingredient) {
    is ItemIngredientPrototype -> IngredientAmount(ingredients[ingredient.name.value]!!, ingredient.amount.toDouble())
    is FluidIngredientPrototype -> IngredientAmount(ingredients[ingredient.name.value]!!, ingredient.amount)
}

fun IngredientsMap.getProductAmount(product: ProductPrototype): IngredientAmount = when (product) {
    is ItemProductPrototype -> {
        val item = ingredients[product.name.value]!!
        val baseAmount: Double = product.amount ?: ((product.amount_min!! + product.amount_max!!).toDouble() / 2.0)
        val amount: Double = (baseAmount + product.extra_count_fraction) * product.probability
        IngredientAmount(item, amount, product.ignored_by_productivity?.toDouble() ?: 0.0)
    }

    is FluidProductPrototype -> {
        val fluid = ingredients[product.name.value]!!
        val baseAmount = product.amount ?: ((product.amount_min!! + product.amount_max!!) / 2.0)
        val amount = baseAmount * product.probability
        IngredientAmount(fluid, amount, product.ignored_by_productivity ?: 0.0)
    }

    is ResearchProgressProductPrototype -> TODO("research progress as product")
}
