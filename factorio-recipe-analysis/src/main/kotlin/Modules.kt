package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import java.util.*
import kotlin.math.sign

fun Float.toIntEffect() = (this * 100 + 1e-6f * sign).toInt().toShort()
data class EffectInt(
    val consumption: Short = 0,
    val speed: Short = 0,
    val productivity: Short = 0,
    val pollution: Short = 0,
    val quality: Short = 0,
) {
    operator fun plus(other: EffectInt) = EffectInt(
        consumption = (consumption + other.consumption).toShort(),
        speed = (speed + other.speed).toShort(),
        productivity = (productivity + other.productivity).toShort(),
        pollution = (pollution + other.pollution).toShort(),
        quality = (quality + other.quality).toShort(),
    )

    val speedMultiplier get() = 1 + speed / 100f
    val prodMultiplier get() = 1 + productivity / 100f
    val qualityChance get() = quality.coerceAtLeast(0) / 1000f
}

data class Module(
    override val prototype: ModulePrototype,
    override val quality: QualityPrototype,
) : Item {
    val effect = prototype.effect.toEffectInt(quality.level.toInt())

    val usedPositiveEffects: EnumSet<EffectType> = EnumSet.noneOf(EffectType::class.java).apply {
        val effect = prototype.effect
        if (effect.consumption != null && effect.consumption!! < 0) add(EffectType.consumption)
        if (effect.speed != null && effect.speed!! > 0) add(EffectType.speed)
        if (effect.productivity != null && effect.productivity!! > 0) add(EffectType.productivity)
        if (effect.pollution != null && effect.pollution!! < 0) add(EffectType.pollution)
        if (effect.quality != null && effect.quality!! > 0) add(EffectType.quality)
    }

    override fun withQuality(quality: QualityPrototype): Module = copy(quality = quality)
}

fun Effect.toEffectInt(qualityLevel: Int): EffectInt {
    val qualityMult = 1.0f + qualityLevel * 0.3f
    fun Float.bonusIfNegative() =
        if (this < 0) this * qualityMult else this

    fun Float.bonusIfPositive() =
        if (this > 0) this * qualityMult else this

    val consumption = consumption?.bonusIfNegative()
    val speed = speed?.bonusIfPositive()
    val productivity = productivity?.bonusIfPositive()
    val pollution = pollution?.bonusIfNegative()
    val quality = quality?.bonusIfPositive()

    return EffectInt(
        consumption = consumption?.toIntEffect() ?: 0,
        speed = speed?.toIntEffect() ?: 0,
        productivity = productivity?.toIntEffect() ?: 0,
        pollution = pollution?.toIntEffect() ?: 0,
        quality = quality?.toIntEffect() ?: 0,
    )
}

data class Beacon(
    override val prototype: BeaconPrototype,
    override val quality: QualityPrototype,
) : Entity {
    private val allowedEffects: EnumSet<EffectType> = prototype.allowed_effects
        ?.let { EnumSet.copyOf(it) }
        ?: EnumSet.allOf(EffectType::class.java)

    fun acceptsModule(module: Module): Boolean =
        allowedEffects.containsAll(module.usedPositiveEffects) &&
                (prototype.allowed_module_categories?.contains(module.prototype.category) ?: true)

    fun effectMultiplier(numBeacons: Int): Double {
        if (numBeacons == 0) return 0.0
        val baseMult = prototype.distribution_effectivity
        val profileMult = prototype.profile?.let {
            val index = numBeacons - 1
            if (index in it.indices) it[index] else it.last()
        } ?: 1.0
        val qualityMult = 1.0 + 0.3 * quality.level.toInt()
        return baseMult * profileMult * qualityMult
    }

    override fun withQuality(quality: QualityPrototype): Beacon = copy(quality = quality)
}

data class BeaconSetup(
    val beacon: Beacon,
    val modules: List<Module>,
) {
    init {
        require(modules.size <= beacon.prototype.module_slots.toInt()) {
            "Too many modules for $beacon"
        }
        require(modules.all { beacon.acceptsModule(it) }) {
            "Module not accepted by $beacon"
        }
    }

    /**
     * Note: each beacon INDIVIDUALLY rounds the effect before applying.
     */
    fun getEffect(numBeacons: Int): EffectInt {
        val multiplier = beacon.effectMultiplier(numBeacons)
        val consumption = modules.sumOf { it.effect.consumption.toInt() }
        val speed = modules.sumOf { it.effect.speed.toInt() }
        val productivity = modules.sumOf { it.effect.productivity.toInt() }
        val pollution = modules.sumOf { it.effect.pollution.toInt() }
        val quality = modules.sumOf { it.effect.quality.toInt() }
        return EffectInt(
            consumption = (consumption * multiplier).toInt().toShort(),
            speed = (speed * multiplier).toInt().toShort(),
            productivity = (productivity * multiplier).toInt().toShort(),
            pollution = (pollution * multiplier).toInt().toShort(),
            quality = (quality * multiplier).toInt().toShort(),
        )
    }
}

fun Beacon.withModules(modules: List<Module>) = BeaconSetup(this, modules)
fun Beacon.withModules(vararg modules: Module) = withModules(modules.asList())

fun getTotalMachineEffect(
    modules: List<Module> = emptyList(),
    beacons: List<BeaconSetup> = emptyList(),
    baseEffect: EffectInt = EffectInt(),
): EffectInt {
    var result = baseEffect
    for (module in modules) result += module.effect
    for (beacon in beacons) result += beacon.getEffect(beacons.size)
    return result
}
