package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.AssemblingMachinePrototype
import glassbricks.factorio.prototypes.CraftingMachinePrototype
import glassbricks.factorio.prototypes.EffectType
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
    val modulesUsed: List<Module>
}

interface AnyCraftingMachine : AnyMachine, WithEffects {
    val prototype: CraftingMachinePrototype
    fun acceptsModule(module: Module): Boolean
}

val AnyCraftingMachine.finalCraftingSpeed get() = baseCraftingSpeed * effects.speedMultiplier
fun AnyCraftingMachine.acceptsRecipe(recipe: CraftingRecipe): Boolean {
    val prototype = prototype
    if (recipe.prototype.category !in prototype.crafting_categories) return false
    if (prototype is AssemblingMachinePrototype) {
        if (prototype.fixed_recipe.value.isNotEmpty() && prototype.fixed_recipe.value != recipe.prototype.name) return false
    }
    // simplified, not 100% accurate, but good enough for now
    if (recipe.inputs.keys.any { it is Fluid } || recipe.outputs.keys.any { it is Fluid }) {
        if (prototype.fluid_boxes.isNullOrEmpty()) return false
    }
    return true
}

data class CraftingMachine(
    override val prototype: CraftingMachinePrototype,
    override val quality: Quality,
) : Entity, AnyCraftingMachine {
    override val effects = prototype.effect_receiver?.base_effect?.toEffectInt(0) ?: IntEffects()
    override val baseCraftingSpeed: Double get() = prototype.crafting_speed * (1.0 + quality.level * 0.3)
    override val modulesUsed: List<Module> get() = emptyList()

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
}

data class MachineWithModules(
    val machine: CraftingMachine,
    val modules: List<Module>,
    val beacons: List<BeaconSetup> = emptyList(),
) : AnyCraftingMachine by machine {
    init {
        require(modules.size <= machine.prototype.module_slots.toInt()) {
            "Machine ${machine.prototype.name} has ${machine.prototype.module_slots} module slots, but ${modules.size} modules were provided"
        }
        for (module in modules) {
            require(machine.acceptsModule(module)) {
                "Module ${module.prototype.name} is not accepted by machine ${machine.prototype.name}"
            }
        }
        require(!(beacons.isNotEmpty() && machine.prototype.effect_receiver?.uses_beacon_effects == false)) {
            "Machine ${machine.prototype.name} does not use beacon effects, cannot apply beacons"
        }
        for ((_, modules) in beacons) {
            for (module in modules) {
                require(machine.acceptsModule(module)) {
                    "Module ${module.prototype.name} (in beacon) is not accepted by machine ${machine.prototype.name}"
                }
            }
        }
    }

    override val modulesUsed: List<Module> get() = modules + beacons.flatMap { it.modules }

    override val effects: IntEffects = machine.effects + modules + totalBeaconEffect(beacons)
}

fun CraftingMachine.withModules(
    modules: List<Module>,
    beacons: List<BeaconSetup> = emptyList(),
) = MachineWithModules(this, modules, beacons)

fun CraftingMachine.withModules(
    vararg modules: Module,
    beacons: List<BeaconSetup> = emptyList(),
) = withModules(modules.asList(), beacons)
