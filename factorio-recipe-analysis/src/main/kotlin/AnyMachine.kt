package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EffectType
import glassbricks.factorio.prototypes.EntityPrototype
import glassbricks.factorio.prototypes.MachinePrototype
import glassbricks.recipeanalysis.IngredientVector
import glassbricks.recipeanalysis.basisVec
import glassbricks.recipeanalysis.emptyVector
import glassbricks.recipeanalysis.plus
import java.util.*

/**
 * Either:
 * - Crafting machine
 * - Mining drills
 * Possibly more in the future
 */
sealed interface AnyMachine<P> : WithEffects, WithBuildCost
        where P : MachinePrototype, P : EntityPrototype {
    val prototype: P
    val baseCraftingSpeed: Double
    val modulesUsed: Iterable<Module>
    fun acceptsModule(module: Module): Boolean
    fun withQuality(quality: Quality): AnyMachine<P>
}

val AnyMachine<*>.finalCraftingSpeed get() = baseCraftingSpeed * effects.speedMultiplier
val MachinePrototype.name get() = (this as EntityPrototype).name

sealed class BaseMachine<P> : AnyMachine<P>, Entity, ModuleInstall<AnyMachine<P>>
        where P : MachinePrototype, P : EntityPrototype {
    private var _effects: IntEffects? = null
    override val effects
        get() = _effects ?: prototype.effect_receiver?.base_effect?.toEffectInt(0) ?: IntEffects()
            .also { _effects = it }
    override val modulesUsed: Iterable<Module> get() = emptySet()

    private var _allowedEffects: EnumSet<EffectType>? = null
    private val allowedEffects
        get() = _allowedEffects ?: EnumSet.noneOf(EffectType::class.java).apply {
            prototype.allowed_effects?.let { addAll(it) }
        }.also { _allowedEffects = it }

    override fun acceptsModule(module: Module): Boolean {
        if (prototype.module_slots.let { it == null || it.toInt() == 0 } ||
            prototype.effect_receiver?.uses_module_effects == false
        ) return false
        return allowedEffects.containsAll(module.usedPositiveEffects)
                && (prototype.allowed_module_categories?.let { module.prototype.category in it } != false)
    }

    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector {
        val itemCost =
            prototypes.itemOfOrNull(prototype as EntityPrototype)?.withQuality(quality) ?: return emptyVector()
        return basisVec(itemCost)
    }

    abstract override fun withQuality(quality: Quality): BaseMachine<P>

    fun acceptsModules(modules: ModuleSet): Boolean {
        for (module in modules.modulesUsed()) if (!acceptsModule(module)) return false
        return modules.beacons.isEmpty() || prototype.effect_receiver?.uses_beacon_effects != false
    }

    override fun withModulesOrNull(modules: ModuleSet): AnyMachine<P>? {
        if (!acceptsModules(modules)) return null
        if (modules.isEmpty()) return this
        return MachineWithModules(this, modules)
    }

    override fun toString(): String {
        val prototype = prototype as EntityPrototype
        return if (quality.level == 0) prototype.name
        else "${prototype.name}(${quality.prototype.name})"
    }

}

interface ModuleInstall<P> {
    val prototype: MachinePrototype
    fun withModulesOrNull(modules: ModuleSet): P?
}

fun <T> ModuleInstall<T>.withModulesOrNull(modules: ModuleConfig): T? =
    prototype.module_slots?.toInt()?.let { modules.toModuleSet(it) }?.let { withModulesOrNull(it) }

fun <T> ModuleInstall<T>.withModulesOrNull(
    modules: List<WithModuleCount>,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
): T? = withModulesOrNull(ModuleConfig(modules, fill, beacons))

fun <T> ModuleInstall<T>.withModules(
    modules: List<WithModuleCount>,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
): T = requireNotNull(withModulesOrNull(modules, fill, beacons)) { "Too many modules for $this" }

fun <T> ModuleInstall<T>.withModulesOrNull(
    vararg modules: WithModuleCount,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
): T? = withModulesOrNull(modules.asList(), fill, beacons)

fun <T> ModuleInstall<T>.withModules(
    vararg modules: WithModuleCount,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
): T = withModules(modules.asList(), fill, beacons)

data class MachineWithModules<P>(
    val machine: BaseMachine<P>,
    val moduleSet: ModuleSet,
) : AnyMachine<P> where P : EntityPrototype, P : MachinePrototype {
    init {
        val (modules, beacons) = moduleSet
        require(modules.size <= (machine.prototype.module_slots?.toInt() ?: 0)) {
            "Machine ${machine.prototype.name} has ${machine.prototype.module_slots} module slots, but ${modules.size} modules were provided"
        }
        require(machine.acceptsModules(moduleSet)) {
            "Machine ${machine.prototype.name} does not accept modules $moduleSet"
        }
        require(!(beacons.size > 0 && machine.prototype.effect_receiver?.uses_beacon_effects == false)) {
            "Machine ${machine.prototype.name} does not use beacon effects, cannot apply beacons"
        }
    }

    override val prototype: P get() = machine.prototype
    override val baseCraftingSpeed: Double get() = machine.baseCraftingSpeed
    override fun acceptsModule(module: Module): Boolean = machine.acceptsModule(module)

    override val modulesUsed: List<Module> get() = moduleSet.modulesUsed()
    override val effects: IntEffects get() = machine.effects + moduleSet
    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector =
        machine.getBuildCost(prototypes) + moduleSet.getBuildCost(prototypes)

    override fun withQuality(quality: Quality): MachineWithModules<P> = copy(machine = machine.withQuality(quality))

    override fun toString(): String = "${machine}${moduleSet}"
}
