package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.BeaconPrototype
import glassbricks.factorio.prototypes.Effect
import glassbricks.factorio.prototypes.EffectType
import glassbricks.factorio.prototypes.ModulePrototype
import glassbricks.recipeanalysis.*
import java.util.*
import kotlin.math.sign

fun Float.toIntEffect() = (this * 100 + 1e-6f * sign).toInt().toShort()
data class IntEffects(
    val consumption: Short = 0,
    val speed: Short = 0,
    val productivity: Short = 0,
    val pollution: Short = 0,
    val quality: Short = 0,
) : WithEffects {
    override val effects: IntEffects get() = this

    operator fun plus(other: WithEffects): IntEffects {
        val effects = other.effects
        return IntEffects(
            consumption = (consumption + effects.consumption).toShort(),
            speed = (speed + effects.speed).toShort(),
            productivity = (productivity + effects.productivity).toShort(),
            pollution = (pollution + effects.pollution).toShort(),
            quality = (quality + effects.quality).toShort(),
        )
    }

    operator fun plus(other: Iterable<WithEffects>): IntEffects = other.fold(this, IntEffects::plus)

    operator fun times(multiplier: Double): IntEffects = IntEffects(
        consumption = (consumption * multiplier).toInt().toShort(),
        speed = (speed * multiplier).toInt().toShort(),
        productivity = (productivity * multiplier).toInt().toShort(),
        pollution = (pollution * multiplier).toInt().toShort(),
        quality = (quality * multiplier).toInt().toShort(),
    )

    operator fun times(multiplier: Int): IntEffects = IntEffects(
        consumption = (consumption * multiplier).toShort(),
        speed = (speed * multiplier).toShort(),
        productivity = (productivity * multiplier).toShort(),
        pollution = (pollution * multiplier).toShort(),
        quality = (quality * multiplier).toShort(),
    )

    val speedMultiplier get() = 1 + speed / 100f
    val prodMultiplier get() = (1 + productivity / 100f).coerceAtMost(4f)
    val consumptionMultiplier get() = (1 + consumption / 100f).coerceAtLeast(0.2f)
    val qualityChance get() = quality.coerceAtLeast(0) / 1000f
}

operator fun WithEffects.plus(other: WithEffects): IntEffects = effects + other.effects

interface WithEffects {

    val effects: IntEffects
}

data class Module(
    override val prototype: ModulePrototype,
    override val quality: Quality,
) : Item, WithEffects, WithModuleCount {
    override val effects = prototype.effect.toEffectInt(quality.level)

    override val moduleEffectsUsed: EnumSet<EffectType> = EnumSet.noneOf(EffectType::class.java).apply {
        val effect = prototype.effect
        if (effect.consumption != null && effect.consumption!! < 0) add(EffectType.consumption)
        if (effect.speed != null && effect.speed!! > 0) add(EffectType.speed)
        if (effect.productivity != null && effect.productivity!! > 0) add(EffectType.productivity)
        if (effect.pollution != null && effect.pollution!! < 0) add(EffectType.pollution)
        if (effect.quality != null && effect.quality!! > 0) add(EffectType.quality)
    }

    override val moduleCount: ModuleCount get() = ModuleCount(this, 1)
    override val modulesUsed: Iterable<Module> get() = listOf(this)

    override fun withQuality(quality: Quality): Module = copy(quality = quality)
    override fun toString(): String = if (quality.level == 0) prototype.name else "${prototype.name}(${quality})"
}

data class ModuleCount(val module: Module, val count: Int) : WithModuleCount, WithBuildCost, WithModulesUsed {
    override val moduleCount: ModuleCount get() = this
    override val effects: IntEffects get() = module.effects * count
    override fun toString(): String = if (count == 1) module.toString() else "${module}*${count}"
    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector =
        vectorOf(module to count.toDouble())

    override val modulesUsed: Iterable<Module> get() = listOf(module)
    override val moduleEffectsUsed: EnumSet<EffectType> get() = module.moduleEffectsUsed
}

interface WithModuleCount : WithEffects, WithModulesUsed {
    val moduleCount: ModuleCount
}

operator fun Module.times(count: Int): ModuleCount = ModuleCount(this, count)
operator fun Int.times(module: Module): ModuleCount = ModuleCount(module, this)

@JvmInline
value class ModuleList(val moduleCounts: List<ModuleCount>) : WithEffects, WithBuildCost, WithModulesUsed {
    constructor(vararg modules: WithModuleCount) : this(modules.map { it.moduleCount })

    val size get() = moduleCounts.sumOf { it.count }
    fun isEmpty() = moduleCounts.isEmpty()
    override val effects get() = moduleCounts.fold(IntEffects()) { acc, module -> acc + module.effects }
    override fun toString(): String = moduleCounts.joinToString(", ")
    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector =
        moduleCounts.vectorSumOf { it.getBuildCost(prototypes) }

    override val modulesUsed: Iterable<Module> get() = moduleCounts.map { it.moduleCount.module }
    override val moduleEffectsUsed: EnumSet<EffectType>
        get() = moduleCounts.fold(EnumSet.noneOf(EffectType::class.java)) { acc, module ->
            acc.apply { addAll(module.moduleEffectsUsed) }
        }
}

fun moduleList(
    numSlots: Int,
    modules: List<WithModuleCount>,
    fill: Module? = null,
): ModuleList? {
    if (modules.isEmpty() && fill == null) return ModuleList(emptyList())
    val moduleCounts = modules.map { it.moduleCount }
    val numExisting = moduleCounts.sumOf { it.count }
    if (numExisting > numSlots) return null
    if (fill == null) return ModuleList(moduleCounts)
    return ModuleList(moduleCounts + ModuleCount(fill, numSlots - numExisting))
}

fun moduleList(
    numSlots: Int,
    vararg modules: WithModuleCount,
    fill: Module? = null,
): ModuleList? = moduleList(numSlots, modules.asList(), fill)

fun Effect.toEffectInt(qualityLevel: Int): IntEffects {
    val qualityMult = 1.0f + qualityLevel * 0.3f
    fun Float.bonusIfNegative() =
        if (this < 0.0) this * qualityMult else this

    fun Float.bonusIfPositive() =
        if (this > 0.0) this * qualityMult else this

    val consumption = consumption?.bonusIfNegative()
    val speed = speed?.bonusIfPositive()
    val productivity = productivity?.bonusIfPositive()
    val pollution = pollution?.bonusIfNegative()
    val quality = quality?.bonusIfPositive()

    return IntEffects(
        consumption = consumption?.toIntEffect() ?: 0,
        speed = speed?.toIntEffect() ?: 0,
        productivity = productivity?.toIntEffect() ?: 0,
        pollution = pollution?.toIntEffect() ?: 0,
        quality = quality?.toIntEffect() ?: 0,
    )
}

data class Beacon(
    override val prototype: BeaconPrototype,
    override val quality: Quality,
) : Entity, WithPowerUsage {
    private val allowedEffects: EnumSet<EffectType> = prototype.allowed_effects
        ?.let { EnumSet.copyOf(it) }
        ?: EnumSet.allOf(EffectType::class.java)

    fun acceptsModule(module: Module): Boolean =
        allowedEffects.containsAll(module.moduleEffectsUsed) &&
                (prototype.allowed_module_categories?.contains(module.prototype.category) != false)

    fun effectMultiplier(numBeacons: Int): Double {
        if (numBeacons == 0) return 0.0
        val baseMult = prototype.distribution_effectivity
        val profileMult = prototype.profile?.let {
            val index = numBeacons - 1
            if (index in it.indices) it[index] else it.last()
        } ?: 1.0
        val qualityMult = 1.0 + 0.3 * quality.level
        return baseMult * profileMult * qualityMult
    }

    override val powerUsage: Double = parseEnergy(prototype.energy_usage)

    override fun withQuality(quality: Quality): Beacon = copy(quality = quality)

    override fun toString(): String = if (quality.level == 0) prototype.name else "${prototype.name}(${quality})"
}

data class BeaconSetup(
    val beacon: Beacon,
    val modules: ModuleList,
    val sharing: Double = 1.0,
) : WithBeaconCount, WithBuildCost, WithPowerUsage, WithModulesUsed by modules {
    init {
        require(modules.size <= beacon.prototype.module_slots.toInt()) {
            "Too many modules for $beacon"
        }
        require(modules.moduleCounts.all { beacon.acceptsModule(it.module) }) {
            "Module not accepted by $beacon"
        }
    }

    /** On purpose, each beacon INDIVIDUALLY rounds effects before returning. */
    fun getEffect(numBeacons: Int): IntEffects = modules.effects * beacon.effectMultiplier(numBeacons)

    override val beaconCount: BeaconCount get() = BeaconCount(this, 1)
    override fun toString(): String = "$beacon[$modules]"
    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector {
        val beaconCost = prototypes.itemOfOrNull(beacon)?.let { uvec(it) }.orZero()
        val moduleCost = modules.getBuildCost(prototypes)
        return (moduleCost + beaconCost) / sharing
    }

    override val powerUsage get() = beacon.powerUsage / sharing
}

fun Beacon.withModules(
    modules: List<WithModuleCount>,
    fill: Module? = null,
    sharing: Double = 1.0,
): BeaconSetup {
    val moduleList = moduleList(prototype.module_slots.toInt(), modules, fill)
    val moduleListResult = requireNotNull(moduleList) { "Too many modules for $this" }
    return BeaconSetup(this, moduleListResult, sharing)
}

fun Beacon.withModules(vararg modules: WithModuleCount, fill: Module? = null, sharing: Double = 1.0): BeaconSetup =
    withModules(modules.asList(), fill, sharing)

operator fun Beacon.invoke(vararg modules: WithModuleCount, fill: Module? = null, sharing: Double = 1.0): BeaconSetup =
    withModules(modules.asList(), fill, sharing)

data class BeaconCount(val beaconSetup: BeaconSetup, val count: Int) : WithBeaconCount, WithBuildCost, WithPowerUsage,
    WithModulesUsed by beaconSetup {
    override val beaconCount: BeaconCount get() = this
    fun getEffect(numBeacons: Int): IntEffects = beaconSetup.getEffect(numBeacons) * count
    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector =
        beaconSetup.getBuildCost(prototypes) * count

    override val powerUsage: Double get() = beaconSetup.powerUsage * count

    override fun toString(): String = if (count == 1) beaconSetup.toString() else "$beaconSetup*$count"

}

interface WithBeaconCount : WithModulesUsed {
    val beaconCount: BeaconCount
}

operator fun BeaconSetup.times(count: Int): BeaconCount = BeaconCount(this, count)
operator fun Int.times(beacon: BeaconSetup): BeaconCount = BeaconCount(beacon, this)

@JvmInline
value class BeaconList(val beaconCounts: List<BeaconCount>) : WithEffects, WithBuildCost, WithPowerUsage,
    WithModulesUsed {
    constructor(vararg beacons: WithBeaconCount) : this(beacons.map { it.beaconCount })

    val size get() = beaconCounts.sumOf { it.count }
    fun isEmpty() = beaconCounts.isEmpty()

    override val effects: IntEffects
        get() = beaconCounts.fold(IntEffects()) { acc, beacon -> acc + beacon.getEffect(size) }
    override val powerUsage: Double
        get() = beaconCounts.sumOf { it.powerUsage }
    override val modulesUsed: Iterable<Module>
        get() = beaconCounts.flatMap { it.modulesUsed }
    override val moduleEffectsUsed: EnumSet<EffectType>
        get() = beaconCounts.fold(EnumSet.noneOf(EffectType::class.java)) { acc, beacon ->
            acc.apply { addAll(beacon.moduleEffectsUsed) }
        }

    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector =
        beaconCounts.vectorSumOf { it.getBuildCost(prototypes) }

    override fun toString(): String = beaconCounts.joinToString(", ")
}

data class ModuleSet(
    val modules: ModuleList = ModuleList(emptyList()),
    val beacons: BeaconList = BeaconList(emptyList()),
) : WithEffects, WithBuildCost, WithPowerUsage, WithModulesUsed {
    override val effects: IntEffects = modules + beacons.effects

    override val modulesUsed: Iterable<Module>
        get() = buildList {
            for ((module) in modules.moduleCounts) {
                add(module)
            }
            for ((beacon) in beacons.beaconCounts) {
                for ((module) in beacon.modules.moduleCounts) {
                    add(module)
                }
            }
        }

    override val moduleEffectsUsed: EnumSet<EffectType> =
        modules.moduleEffectsUsed
            .clone().apply { addAll(beacons.moduleEffectsUsed) }

    fun isEmpty() = modules.isEmpty() && beacons.isEmpty()

    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector =
        modules.getBuildCost(prototypes) + beacons.getBuildCost(prototypes)

    override val powerUsage: Double get() = beacons.powerUsage

    override fun toString(): String = buildString {
        append('[')
        append(modules)
        if (beacons.size > 0) {
            append(", ")
            append(beacons)
        }
        append(']')
    }
}

data class ModuleSetConfig(
    val modules: List<ModuleCount> = emptyList(),
    val fill: Module? = null,
    val beacons: List<BeaconCount> = emptyList(),
) : WithModulesUsed {

    override val modulesUsed: Iterable<Module> = buildList {
        for (module in modules) add(module.module)
        if (fill != null) add(fill)
        for (beacon in beacons) addAll(beacon.modulesUsed)
    }
    override val moduleEffectsUsed: EnumSet<EffectType>
        get() = modules.fold(EnumSet.noneOf(EffectType::class.java)) { acc, module ->
            acc.apply { addAll(module.module.moduleEffectsUsed) }
        }

    fun toModuleSet(numModuleSlots: Int): ModuleSet? = moduleList(numModuleSlots, modules, fill)
        ?.let { ModuleSet(it, BeaconList(beacons)) }
}

fun ModuleSetConfig(
    modules: List<WithModuleCount>,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
) = ModuleSetConfig(modules.map { it.moduleCount }, fill, beacons.map { it.beaconCount })
