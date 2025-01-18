package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.FluidPrototype
import glassbricks.factorio.prototypes.ItemPrototype
import glassbricks.factorio.prototypes.ModulePrototype
import glassbricks.factorio.prototypes.Prototype
import glassbricks.recipeanalysis.Ingredient

sealed interface RealIngredient : Ingredient {
    val prototype: Prototype
}

sealed interface Item : RealIngredient {
    override val prototype: ItemPrototype
    val quality: Quality
    fun withQuality(quality: Quality): Item
}

data class BasicItem(
    override val prototype: ItemPrototype,
    override val quality: Quality,
) : Item {
    override fun withQuality(quality: Quality): BasicItem = copy(quality = quality)
    override fun toString(): String = if (quality.level == 0) {
        prototype.name
    } else {
        "${prototype.name}(${quality})"
    }
}

internal fun getItem(
    prototype: ItemPrototype,
    quality: Quality,
): Item = when (prototype) {
    is ModulePrototype -> Module(prototype, quality)
    else -> BasicItem(prototype, quality)
}

data class Fluid(override val prototype: FluidPrototype) : RealIngredient
