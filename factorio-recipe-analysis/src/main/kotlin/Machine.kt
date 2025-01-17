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

sealed interface AnyMachine : AnyMachineSetup

class Machine(override val prototype: CraftingMachinePrototype) : Entity, AnyMachine {
    override val appliedEffects = prototype.effect_receiver?.base_effect?.toEffectInt(0) ?: EffectInt()
    override val baseCraftingSpeed: Double get() = prototype.crafting_speed

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
}

data class MachineWithQuality(
    val machine: Machine,
    val qualityLevel: Int,
) : AnyMachine {
    override val prototype: CraftingMachinePrototype
        get() = machine.prototype
    override val baseCraftingSpeed: Double get() = machine.baseCraftingSpeed * (1.0 + qualityLevel * 0.3)
    override val appliedEffects: EffectInt get() = machine.appliedEffects
}

tailrec fun AnyMachineSetup.baseMachine(): Machine = when (this) {
    is Machine -> this
    is MachineWithQuality -> machine
    is MachineWithModules -> machine.baseMachine()
}

fun AnyMachine.withQualityLevel(level: Int) = MachineWithQuality(baseMachine(), level)
fun AnyMachine.withQuality(quality: QualityPrototype) = withQualityLevel(quality.level.toInt())

fun AnyMachineSetup.acceptsModule(module: AnyModule) = baseMachine().acceptsModule(module.baseModule())

data class MachineWithModules(
    val machine: AnyMachine,
    val modules: List<AnyModule>,
    val beacons: List<BeaconWithModules> = emptyList(),
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

fun AnyMachine.withModules(
    modules: List<AnyModule>,
    beacons: List<BeaconWithModules> = emptyList(),
) = MachineWithModules(this, modules, beacons)

fun AnyMachine.withModules(
    vararg modules: AnyModule,
    beacons: List<BeaconWithModules> = emptyList(),
) = withModules(modules.asList(), beacons)
