package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.ItemPrototype
import glassbricks.factorio.prototypes.ModulePrototype


sealed interface Item {
    val prototype: ItemPrototype
}

data class BasicItem(override val prototype: ItemPrototype) : Item

internal fun getItem(prototype: ItemPrototype): Item = when (prototype) {
    is ModulePrototype -> Module(prototype)
    else -> BasicItem(prototype)
}
