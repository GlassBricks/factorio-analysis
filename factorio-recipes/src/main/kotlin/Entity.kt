package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EffectType
import glassbricks.factorio.prototypes.EntityPrototype
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Vector
import java.util.*

interface Entity {
    val quality: Quality
    val prototype: EntityPrototype
    fun withQuality(quality: Quality): Entity
}

interface WithBuildCost {
    fun getBuildCost(prototypes: FactorioPrototypes): Vector<Ingredient>
}

interface WithPowerUsage {
    val powerUsage: Double
}

interface WithModulesUsed {
    val modulesUsed: Iterable<Module>
    val moduleEffectsUsed: EnumSet<EffectType>
}
