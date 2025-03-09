package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityPrototype
import glassbricks.factorio.prototypes.MachinePrototype
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Vector
import glassbricks.recipeanalysis.plus

data class MachineWithModules<P>(
    val machine: BaseMachine<P>,
    override val moduleSet: ModuleSet,
) : AnyMachine<P> where P : MachinePrototype, P : EntityPrototype {
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
    override val quality: Quality get() = machine.quality
    override val baseCraftingSpeed: Double get() = machine.baseCraftingSpeed
    override fun acceptsModule(module: Module): Boolean = machine.acceptsModule(module)

    override val modulesUsed: List<Module> get() = moduleSet.modulesUsed()
    override fun canProcess(process: RecipeOrResource<*>): Boolean {
        if (process is Recipe && !process.acceptsModules(modulesUsed)) return false
        return machine.canProcess(process)
    }

    override val effects: IntEffects get() = machine.effects + moduleSet
    override fun getBuildCost(prototypes: FactorioPrototypes): Vector<Ingredient> =
        machine.getBuildCost(prototypes) + moduleSet.getBuildCost(prototypes)

    override val basePowerUsage: Double get() = machine.basePowerUsage
    override val powerUsage: Double
        get() = machine.powerUsage * effects.consumptionMultiplier + moduleSet.powerUsage

    override fun withQuality(quality: Quality): MachineWithModules<P> = copy(machine = machine.withQuality(quality))

    override fun toString(): String = "${machine}${moduleSet}"
}
