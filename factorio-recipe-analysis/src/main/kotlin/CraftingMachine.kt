package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.AssemblingMachinePrototype
import glassbricks.factorio.prototypes.CraftingMachinePrototype
import glassbricks.factorio.prototypes.EffectType
import glassbricks.recipeanalysis.IngredientVector
import glassbricks.recipeanalysis.basisVec
import glassbricks.recipeanalysis.emptyVector
import java.util.*

/**
 * Any machine that does processes:
 * - Crafting machines
 * - Mining drills (produces ore)
 * - Offshore pumps
 * Possibly more in the future
 */
sealed interface AnyMachine {
    val baseCraftingSpeed: Double
    val modulesUsed: Iterable<Module>
}

interface AnyCraftingMachine : AnyMachine, WithEffects, WithBuildCost {
    val prototype: CraftingMachinePrototype
    fun acceptsModule(module: Module): Boolean
    fun withMachineQuality(quality: Quality): AnyCraftingMachine
}

val AnyCraftingMachine.finalCraftingSpeed get() = baseCraftingSpeed * effects.speedMultiplier
fun AnyCraftingMachine.acceptsRecipe(recipe: Recipe): Boolean {
    val prototype = prototype
    if (recipe.prototype.category !in prototype.crafting_categories) return false
    if (prototype is AssemblingMachinePrototype) {
        if (prototype.fixed_recipe.value.isNotEmpty() && prototype.fixed_recipe.value != recipe.prototype.name) return false
    }
    // simplified, not 100% accurate, but good enough for now
    if (recipe.inputs.keys.any { it is Fluid } || recipe.outputs.keys.any { it is Fluid }) {
        if (prototype.fluid_boxes.isNullOrEmpty()) return false
    }
    if (!recipe.acceptsModules(this.modulesUsed)) return false
    return true
}

data class CraftingMachine(
    override val prototype: CraftingMachinePrototype,
    override val quality: Quality,
) : Entity, AnyCraftingMachine {
    override val effects = prototype.effect_receiver?.base_effect?.toEffectInt(0) ?: IntEffects()
    override val baseCraftingSpeed: Double get() = prototype.crafting_speed * (1.0 + quality.level * 0.3)
    override val modulesUsed: Iterable<Module> get() = emptySet()

    private val allowedEffects: EnumSet<EffectType> = EnumSet.noneOf(EffectType::class.java).apply {
        prototype.allowed_effects?.let { addAll(it) }
    }

    override fun acceptsModule(module: Module): Boolean {
        if (prototype.module_slots.toInt() == 0 ||
            prototype.effect_receiver?.uses_module_effects == false
        ) return false
        return allowedEffects.containsAll(module.usedPositiveEffects)
                && (prototype.allowed_module_categories?.let { module.prototype.category in it } ?: true)
    }

    override fun withQuality(quality: Quality): CraftingMachine = copy(quality = quality)
    override fun withMachineQuality(quality: Quality): CraftingMachine = withQuality(quality)

    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector {
        val itemCost = prototypes.itemToBuild(prototype)?.withQuality(quality) ?: return emptyVector()
        return basisVec(itemCost)
    }

    override fun toString(): String = if (quality.level == 0) prototype.name
    else "${prototype.name}(${quality.prototype.name})"
}

data class MachineWithModules(
    val machine: CraftingMachine,
    val modules: ModuleList,
    val beacons: BeaconList = BeaconList(emptyList()),
) : AnyCraftingMachine by machine {
    init {
        require(modules.size <= machine.prototype.module_slots.toInt()) {
            "Machine ${machine.prototype.name} has ${machine.prototype.module_slots} module slots, but ${modules.size} modules were provided"
        }
        for ((module) in modules.moduleCounts) {
            require(machine.acceptsModule(module)) {
                "Module ${module.prototype.name} is not accepted by machine ${machine.prototype.name}"
            }
        }
        require(!(beacons.size > 0 && machine.prototype.effect_receiver?.uses_beacon_effects == false)) {
            "Machine ${machine.prototype.name} does not use beacon effects, cannot apply beacons"
        }
        for ((beacon) in beacons.beaconCounts) {
            for ((module, _) in beacon.modules.moduleCounts) {
                require(machine.acceptsModule(module)) {
                    "Module ${module.prototype.name} (in beacon) is not accepted by machine ${machine.prototype.name}"
                }
            }
        }
    }

    override val modulesUsed: List<Module>
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
    override val effects: IntEffects = machine.effects + modules + beacons.effects
    override fun withMachineQuality(quality: Quality): MachineWithModules = copy(machine = machine.withQuality(quality))
    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector =
        machine.getBuildCost(prototypes) + modules.getBuildCost(prototypes) + beacons.getBuildCost(prototypes)

    override fun toString(): String = buildString {
        append(machine)
        append('[')
        append(modules)
        if (beacons.size > 0) {
            append(", ")
            append(beacons)
        }
        append(']')
    }
}

fun CraftingMachine.withModulesOrNull(
    modules: List<WithModuleCount>,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
): AnyCraftingMachine? {
    val moduleList = moduleList(prototype.module_slots.toInt(), modules, fill) ?: return null
    if (moduleList.isEmpty() && beacons.isEmpty()) return this
    for ((module) in moduleList.moduleCounts) {
        if (!acceptsModule(module)) return null
    }
    return MachineWithModules(this, moduleList, BeaconList(beacons))
}

fun CraftingMachine.withModules(
    modules: List<WithModuleCount>,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
): AnyCraftingMachine = requireNotNull(withModulesOrNull(modules, fill, beacons)) { "Too many modules for $this" }

fun CraftingMachine.withModulesOrNull(
    vararg modules: WithModuleCount,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
): AnyCraftingMachine? = withModulesOrNull(modules.asList(), fill, beacons)

fun CraftingMachine.withModules(
    vararg modules: WithModuleCount,
    fill: Module? = null,
    beacons: List<WithBeaconCount> = emptyList(),
): AnyCraftingMachine = withModules(modules.asList(), fill, beacons)
