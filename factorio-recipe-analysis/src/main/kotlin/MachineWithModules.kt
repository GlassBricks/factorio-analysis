package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityPrototype
import glassbricks.factorio.prototypes.MachinePrototype
import glassbricks.recipeanalysis.IngredientVector
import glassbricks.recipeanalysis.plus

data class MachineWithModules<P>(
    val machine: BaseMachine<P>,
    val moduleSet: ModuleSet,
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
    override val baseCraftingSpeed: Double get() = machine.baseCraftingSpeed
    override fun acceptsModule(module: Module): Boolean = machine.acceptsModule(module)

    override val modulesUsed: List<Module> get() = moduleSet.modulesUsed()
    override fun canProcess(recipe: RecipeOrResource<*>): Boolean {
        if (recipe is Recipe && !recipe.acceptsModules(modulesUsed)) return false
        return machine.canProcess(recipe)
    }

    override val effects: IntEffects get() = machine.effects + moduleSet
    override fun getBuildCost(prototypes: FactorioPrototypes): IngredientVector =
        machine.getBuildCost(prototypes) + moduleSet.getBuildCost(prototypes)

    override fun withQuality(quality: Quality): MachineWithModules<P> = copy(machine = machine.withQuality(quality))

    override fun toString(): String = "${machine}${moduleSet}"
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
