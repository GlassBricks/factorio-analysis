package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityWithOwnerPrototype
import glassbricks.recipeanalysis.IngredientVector

interface Entity {
    val prototype: EntityWithOwnerPrototype
    val quality: Quality

    fun withQuality(quality: Quality): Entity
}

interface WithBuildCost {
    fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector
}
