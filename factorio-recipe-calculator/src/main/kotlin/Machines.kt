package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.*
import glassbricks.recipeanalysis.IngredientVector
import glassbricks.recipeanalysis.basisVec
import glassbricks.recipeanalysis.emptyVector
import java.util.*

/**
 * Either:
 * - Crafting machine
 * - Mining drills
 * Possibly more in the future
 */
interface AnyMachine<P : MachinePrototype> : WithEffects, WithBuildCost {
    val prototype: P
    val baseCraftingSpeed: Double
    val modulesUsed: Iterable<Module>
    fun acceptsModule(module: Module): Boolean
    fun canProcess(process: RecipeOrResource<*>): Boolean
    val quality: Quality
    fun withQuality(quality: Quality): AnyMachine<P>
}

val AnyMachine<*>.finalCraftingSpeed get() = baseCraftingSpeed * effects.speedMultiplier
val MachinePrototype.name get() = (this as EntityPrototype).name

data class CraftingMachine(
    override val prototype: CraftingMachinePrototype,
    override val quality: Quality,
) : BaseMachine<CraftingMachinePrototype>(), AnyCraftingMachine {
    override val baseCraftingSpeed: Double get() = prototype.crafting_speed * (1.0 + quality.level * 0.3)
    override fun withQuality(quality: Quality): CraftingMachine = CraftingMachine(prototype, quality)

    override fun canProcess(process: RecipeOrResource<*>): Boolean {
        if (process !is Recipe) return false
        val machinePrototype = prototype
        if (process.prototype.category !in machinePrototype.crafting_categories) return false
        if (machinePrototype is AssemblingMachinePrototype) {
            if (machinePrototype.fixed_recipe.value.isNotEmpty() && machinePrototype.fixed_recipe.value != process.prototype.name) return false
        }
        // simplified, not 100% accurate, but good enough for now
        if (process.inputs.keys.any { it is Fluid } || process.outputs.keys.any { it is Fluid }) {
            if (machinePrototype.fluid_boxes.isNullOrEmpty()) return false
        }
        return true
    }
}
typealias AnyCraftingMachine = AnyMachine<CraftingMachinePrototype>
typealias CraftingMachineWithModules = MachineWithModules<CraftingMachinePrototype>

data class MiningDrill(
    override val prototype: MiningDrillPrototype,
    override val quality: Quality,
) : BaseMachine<MiningDrillPrototype>(), AnyMiningDrill {
    // higher quality miners don't mine faster
    override val baseCraftingSpeed: Double get() = prototype.mining_speed
    override fun withQuality(quality: Quality): MiningDrill = MiningDrill(prototype, quality)
    override fun canProcess(process: RecipeOrResource<*>): Boolean =
        process is Resource && process.prototype.category in this.prototype.resource_categories
}
typealias AnyMiningDrill = AnyMachine<MiningDrillPrototype>
typealias MiningDrillWithModules = MachineWithModules<MiningDrillPrototype>

sealed class BaseMachine<P> : AnyMachine<P>, Entity
        where P : MachinePrototype, P : EntityPrototype {
    private var _effects: IntEffects? = null
    override val effects
        get() = _effects ?: prototype.effect_receiver?.base_effect?.toEffectInt(0) ?: IntEffects()
            .also { _effects = it }
    override val modulesUsed: Iterable<Module> get() = emptySet()

    private var _allowedEffects: EnumSet<EffectType>? = null
    private val allowedEffects
        get() = _allowedEffects ?: run {
            val allowedEffects = prototype.allowed_effects
            if (allowedEffects == null) EnumSet.allOf(EffectType::class.java)
            else EnumSet.noneOf(EffectType::class.java).apply { addAll(allowedEffects) }
        }
            .also { _allowedEffects = it }

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

    fun withModulesOrNull(modules: ModuleSet): AnyMachine<P>? {
        if (!acceptsModules(modules)) return null
        if (modules.isEmpty()) return this
        return MachineWithModules(this, modules)
    }

    fun withModulesOrNull(modules: ModuleConfig): AnyMachine<P>? =
        prototype.module_slots?.toInt()?.let { modules.toModuleSet(it) }?.let { withModulesOrNull(it) }

    fun withModulesOrNull(
        modules: List<WithModuleCount>,
        fill: Module? = null,
        beacons: List<WithBeaconCount> = emptyList(),
    ) = withModulesOrNull(ModuleConfig(modules, fill, beacons))

    fun withModules(
        modules: List<WithModuleCount>,
        fill: Module? = null,
        beacons: List<WithBeaconCount> = emptyList(),
    ) = requireNotNull(withModulesOrNull(modules, fill, beacons)) { "Too many modules for $this" }

    fun withModulesOrNull(
        vararg modules: WithModuleCount,
        fill: Module? = null,
        beacons: List<WithBeaconCount> = emptyList(),
    ) = withModulesOrNull(modules.asList(), fill, beacons)

    fun withModules(
        vararg modules: WithModuleCount,
        fill: Module? = null,
        beacons: List<WithBeaconCount> = emptyList(),
    ) = withModules(modules.asList(), fill, beacons)

    final override fun toString(): String {
        val prototype = prototype as EntityPrototype
        return if (quality.level == 0) prototype.name
        else "${prototype.name}(${quality.prototype.name})"
    }
}
