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

sealed interface AnyModule {
    val effect: EffectInt
    val prototype: ModulePrototype
    val usedPositiveEffects: EnumSet<EffectType>
}

fun AnyModule.baseModule(): Module = when (this) {
    is Module -> this
    is ModuleWithQuality -> module
}

data class Module(override val prototype: ModulePrototype) : Item, AnyModule {
    override val effect get() = prototype.effect.toEffectInt(0)

    override val usedPositiveEffects: EnumSet<EffectType> = EnumSet.noneOf(EffectType::class.java).apply {
        val effect = prototype.effect
        if (effect.consumption != null && effect.consumption!! < 0) add(EffectType.consumption)
        if (effect.speed != null && effect.speed!! > 0) add(EffectType.speed)
        if (effect.productivity != null && effect.productivity!! > 0) add(EffectType.productivity)
        if (effect.pollution != null && effect.pollution!! < 0) add(EffectType.pollution)
        if (effect.quality != null && effect.quality!! > 0) add(EffectType.quality)
    }
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

data class ModuleWithQuality(val module: Module, val qualityLevel: Int) : AnyModule {
    override val effect get() = module.prototype.effect.toEffectInt(qualityLevel = qualityLevel)
    override val prototype: ModulePrototype get() = module.prototype
    override val usedPositiveEffects: EnumSet<EffectType> get() = module.usedPositiveEffects
}

fun AnyModule.withQualityLevel(level: Int) = ModuleWithQuality(baseModule(), level)
fun AnyModule.withQuality(quality: QualityPrototype) = withQualityLevel(quality.level.toInt())

sealed interface AnyBeacon {
    val prototype: BeaconPrototype
    fun effectMultiplier(numBeacons: Int): Double
}

class Beacon(override val prototype: BeaconPrototype) : Entity, AnyBeacon {
    private val allowedEffects: EnumSet<EffectType> = prototype.allowed_effects
        ?.let { EnumSet.copyOf(it) }
        ?: EnumSet.allOf(EffectType::class.java)

    fun acceptsModule(module: Module): Boolean =
        allowedEffects.containsAll(module.usedPositiveEffects) &&
                (prototype.allowed_module_categories?.contains(module.prototype.category) ?: true)

    fun effectMultiplier(
        qualityLevel: Int,
        numBeacons: Int,
    ): Double {
        if (numBeacons == 0) return 0.0
        val baseMult = prototype.distribution_effectivity
        val profileMult = prototype.profile?.let {
            val index = numBeacons - 1
            if (index in it.indices) it[index] else it.last()
        } ?: 1.0
        val qualityMult = 1.0 + 0.3 * qualityLevel
        return baseMult * profileMult * qualityMult
    }

    override fun effectMultiplier(numBeacons: Int): Double = effectMultiplier(0, numBeacons)
}

data class BeaconWithQuality(
    val beacon: Beacon,
    val qualityLevel: Int,
) : AnyBeacon {
    override val prototype get() = beacon.prototype
    override fun effectMultiplier(numBeacons: Int): Double = beacon.effectMultiplier(qualityLevel, numBeacons)
}

fun AnyBeacon.baseBeacon(): Beacon = when (this) {
    is Beacon -> this
    is BeaconWithQuality -> beacon
}

fun AnyBeacon.withQualityLevel(level: Int) = BeaconWithQuality(baseBeacon(), level)
fun AnyBeacon.withQuality(quality: QualityPrototype) = withQualityLevel(quality.level.toInt())

fun AnyBeacon.acceptsModule(module: AnyModule): Boolean = baseBeacon().acceptsModule(module.baseModule())

data class BeaconWithModules(
    val beacon: AnyBeacon,
    val modules: List<AnyModule>,
) {
    init {
        require(modules.size <= beacon.prototype.module_slots.toInt()) {
            "Too many modules for $beacon"
        }
        require(modules.all { beacon.acceptsModule(it) }) {
            "Module not accepted by $beacon"
        }
    }

    companion object {
        fun tryCreate(beacon: BeaconWithQuality, modules: List<ModuleWithQuality>) =
            runCatching { BeaconWithModules(beacon, modules) }
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

fun AnyBeacon.withModules(modules: List<AnyModule>) = BeaconWithModules(this, modules)
fun AnyBeacon.withModules(vararg modules: AnyModule) = withModules(modules.asList())

fun getTotalMachineEffect(
    modules: List<AnyModule> = emptyList(),
    beacons: List<BeaconWithModules> = emptyList(),
    baseEffect: EffectInt = EffectInt(),
): EffectInt {
    var result = baseEffect
    for (module in modules) result += module.effect
    for (beacon in beacons) result += beacon.getEffect(beacons.size)
    return result
}
