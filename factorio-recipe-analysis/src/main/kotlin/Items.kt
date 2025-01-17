package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.Effect
import glassbricks.factorio.prototypes.EffectType
import glassbricks.factorio.prototypes.ItemPrototype
import glassbricks.factorio.prototypes.ModulePrototype
import java.util.*


sealed interface Item {
    val prototype: ItemPrototype
}

data class BasicItem(override val prototype: ItemPrototype) : Item

data class Module(override val prototype: ModulePrototype) : Item {
    fun effect(qualityLevel: Int): Effect {
        val baseEffect = prototype.effect
        return if (qualityLevel == 0) baseEffect else {
            val qualityMult = 1.0f + qualityLevel * 0.3f
            fun Float.roundHundreds() = (this * 100 + 1e-6).toInt() / 100f
            fun Float.bonusIfNegative() =
                if (this < 0) (this * qualityMult).roundHundreds() else this

            fun Float.bonusIfPositive() =
                if (this > 0) (this * qualityMult).roundHundreds() else this

            val consumption = baseEffect.consumption?.bonusIfNegative()
            val speed = baseEffect.speed?.bonusIfPositive()
            val productivity = baseEffect.productivity?.bonusIfPositive()
            val pollution = baseEffect.pollution?.bonusIfNegative()
            val quality = baseEffect.quality?.bonusIfPositive()

            return Effect(
                consumption = consumption,
                speed = speed,
                productivity = productivity,
                pollution = pollution,
                quality = quality
            )
        }
    }

    val usedPositiveEffects: EnumSet<EffectType> = EnumSet.noneOf(EffectType::class.java).apply {
        val effect = prototype.effect
        if (effect.consumption != null && effect.consumption!! < 0) add(EffectType.consumption)
        if (effect.speed != null && effect.speed!! > 0) add(EffectType.speed)
        if (effect.productivity != null && effect.productivity!! > 0) add(EffectType.productivity)
        if (effect.pollution != null && effect.pollution!! < 0) add(EffectType.pollution)
        if (effect.quality != null && effect.quality!! > 0) add(EffectType.quality)
    }
}

internal fun getItem(prototype: ItemPrototype): Item = when (prototype) {
    is ModulePrototype -> Module(prototype)
    else -> BasicItem(prototype)
}
