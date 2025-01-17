package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.ItemPrototype
import glassbricks.factorio.prototypes.ModulePrototype
import glassbricks.factorio.prototypes.Prototype
import glassbricks.recipeanalysis.Ingredient

sealed interface RealIngredient : Ingredient {
    val prototype: Prototype
}

sealed interface Item : RealIngredient {
    override val prototype: ItemPrototype
}

data class BasicItem(override val prototype: ItemPrototype) : Item

internal fun getItem(prototype: ItemPrototype): Item = when (prototype) {
    is ModulePrototype -> Module(prototype)
    else -> BasicItem(prototype)
}

sealed interface Fluid : RealIngredient {
    override val prototype: Prototype
}

data class BasicFluid(override val prototype: Prototype) : Fluid
