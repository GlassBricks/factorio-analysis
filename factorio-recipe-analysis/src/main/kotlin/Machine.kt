package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.CraftingMachinePrototype
import glassbricks.factorio.prototypes.EffectType
import glassbricks.factorio.prototypes.QualityPrototype
import java.util.*

sealed interface AnyMachineSetup {
    val prototype: CraftingMachinePrototype
    val baseCraftingSpeed: Double
    val appliedEffects: EffectInt
}

val AnyMachineSetup.finalCraftingSpeed get() = baseCraftingSpeed * appliedEffects.speedMultiplier

data class Machine(
    override val prototype: CraftingMachinePrototype,
    override val quality: QualityPrototype,
) : Entity, AnyMachineSetup {
    override val appliedEffects = prototype.effect_receiver?.base_effect?.toEffectInt(0) ?: EffectInt()
    override val baseCraftingSpeed: Double get() = prototype.crafting_speed * (1.0 + quality.level.toInt() * 0.3)

    private val allowedEffects: EnumSet<EffectType> = EnumSet.noneOf(EffectType::class.java).apply {
        prototype.allowed_effects?.let { addAll(it) }
    }

    fun acceptsModule(module: Module): Boolean {
        if (prototype.module_slots.toInt() == 0 ||
            prototype.effect_receiver?.uses_module_effects == false
        ) return false
        return allowedEffects.containsAll(module.usedPositiveEffects)
                && (prototype.allowed_module_categories?.let { module.prototype.category in it } ?: true)
    }

    override fun withQuality(quality: QualityPrototype): Machine = copy(quality = quality)
}

data class MachineWithModules(
    val machine: Machine,
    val modules: List<Module>,
    val beacons: List<BeaconSetup> = emptyList(),
) : AnyMachineSetup {
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

    override val prototype: CraftingMachinePrototype get() = machine.prototype
    override val appliedEffects: EffectInt =
        getTotalMachineEffect(baseEffect = machine.appliedEffects, modules = modules, beacons = beacons)
    override val baseCraftingSpeed: Double get() = machine.baseCraftingSpeed
}

fun Machine.withModules(
    modules: List<Module>,
    beacons: List<BeaconSetup> = emptyList(),
) = MachineWithModules(this, modules, beacons)

fun Machine.withModules(
    vararg modules: Module,
    beacons: List<BeaconSetup> = emptyList(),
) = withModules(modules.asList(), beacons)
