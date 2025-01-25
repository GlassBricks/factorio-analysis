package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.Process
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.recipelp.LpProcess
import glassbricks.recipeanalysis.recipelp.RecipeLpFormatter

interface FactorioRecipesFormatter : RecipeLpFormatter {
    fun formatSetup(setup: MachineSetup<*>): String =
        "${formatMachine(setup.machine)} --> ${formatProcess(setup.process)}"

    fun formatMachine(machine: AnyMachine<*>): String = when (machine) {
        is BaseMachine<*> -> formatBaseMachine(machine)
        is MachineWithModules -> formatMachineWithModules(machine)
        else -> error("Unknown machine type: $machine")
    }

    fun formatBaseMachine(machine: BaseMachine<*>): String = machine.prototype.name + formatQuality(machine.quality)
    fun formatMachineWithModules(machine: MachineWithModules<*>): String =
        formatBaseMachine(machine.machine) + formatModuleSet(machine.moduleSet)

    fun formatModuleSet(moduleSet: ModuleSet): String = buildString {
        append('[')
        append(formatModuleList(moduleSet.modules))
        if (moduleSet.beacons.isEmpty().not()) {
            append(", ")
            append(formatBeaconList(moduleSet.beacons))
        }
        append(']')
    }

    fun formatModuleList(modules: ModuleList): String =
        modules.moduleCounts.joinToString(", ") { formatModuleCount(it) }

    fun formatModuleCount(moduleCount: ModuleCount): String =
        formatModule(moduleCount.module) + " x${moduleCount.count}"

    fun formatModule(module: Module): String = module.prototype.name + formatQuality(module.quality)

    fun formatBeaconList(beacons: BeaconList): String =
        beacons.beaconCounts.joinToString(", ") { formatBeaconCount(it) }

    fun formatBeaconCount(beaconCount: BeaconCount): String =
        "${formatBeaconSetup(beaconCount.beaconSetup)}x${beaconCount.count}"

    fun formatBeaconSetup(beaconSetup: BeaconSetup): String =
        "${formatBeacon(beaconSetup.beacon)}[${formatModuleList(beaconSetup.modules)}]"

    fun formatBeacon(beacon: Beacon): String = beacon.prototype.name + formatQuality(beacon.quality)

    fun formatProcess(process: RecipeOrResource<*>): String = when (process) {
        is Recipe -> formatRecipe(process)
        is Resource -> formatResource(process)
    }

    fun formatRecipe(recipe: Recipe): String = recipe.prototype.name + formatQuality(recipe.inputQuality)
    fun formatResource(resource: Resource): String = resource.prototype.name
    fun formatQuality(quality: Quality): String = if (quality.level == 0) "" else "(${quality.prototype.name})"

    override fun formatSymbol(symbol: Symbol): String = when (symbol) {
        is Item -> formatItem(symbol)
        is Fluid -> formatFluid(symbol)
        else -> symbol.toString()
    }

    fun formatItem(item: Item): String = item.prototype.name + formatQuality(item.quality)
    fun formatFluid(fluid: Fluid): String = fluid.prototype.name

    override fun formatProcess(process: LpProcess): String = when (val process = process.process) {
        is MachineSetup<*> -> formatSetup(process)
        else -> process.toString()
    }

    companion object Default : FactorioRecipesFormatter
}

interface RecipesFirst : FactorioRecipesFormatter {
    override fun formatSetup(setup: MachineSetup<*>): String =
        "${formatProcess(setup.process)} --> ${formatMachine(setup.machine)}"

    override val processComparator: Comparator<in Process>?
        get() = compareBy {
            if (it is MachineSetup<*>) formatProcess(it.process)
            else null
        }

    companion object : RecipesFirst
}
