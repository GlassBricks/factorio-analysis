package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import glassbricks.recipeanalysis.Ingredient

sealed interface RealIngredient : Ingredient {
    val prototype: Prototype
}

sealed interface Item : RealIngredient {
    override val prototype: ItemPrototype
    val quality: QualityPrototype
    fun withQuality(quality: QualityPrototype): Item
}

data class BasicItem(
    override val prototype: ItemPrototype,
    override val quality: QualityPrototype,
) : Item {
    override fun withQuality(quality: QualityPrototype): BasicItem = copy(quality = quality)
}

internal fun getItem(
    prototype: ItemPrototype,
    quality: QualityPrototype,
): Item = when (prototype) {
    is ModulePrototype -> Module(prototype, quality)
    else -> BasicItem(prototype, quality)
}

data class Fluid(override val prototype: FluidPrototype) : RealIngredient
