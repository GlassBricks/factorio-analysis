package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityPrototype
import glassbricks.recipeanalysis.IngredientVector

interface Entity {
    val quality: Quality
    val prototype: EntityPrototype
    fun withQuality(quality: Quality): Entity
}

interface WithBuildCost {
    fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector
}
