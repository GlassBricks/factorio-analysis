package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.BeaconPrototype
import glassbricks.factorio.prototypes.EffectType
import java.util.*

class Beacon(
    override val prototype: BeaconPrototype,
    override val builtBy: Item?
) : Entity {
    private val allowedEffects: EnumSet<EffectType> = prototype.allowed_effects
        ?.let { EnumSet.copyOf(it) }
        ?: EnumSet.allOf(EffectType::class.java)

    fun acceptsModule(module: Module): Boolean =
        allowedEffects.containsAll(module.usedPositiveEffects) &&
                (prototype.allowed_module_categories?.contains(module.prototype.category) ?: true)
}
