package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityPrototype
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Vector

interface Entity {
    val quality: Quality
    val prototype: EntityPrototype
    fun withQuality(quality: Quality): Entity
}

interface WithBuildCost {
    fun getBuildCost(prototypes: FactorioPrototypes): Vector<Ingredient>
}
