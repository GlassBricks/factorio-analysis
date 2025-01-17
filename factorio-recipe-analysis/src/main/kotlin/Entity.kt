package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityWithOwnerPrototype


interface Entity {
    val prototype: EntityWithOwnerPrototype
    val builtBy: Item?
}
