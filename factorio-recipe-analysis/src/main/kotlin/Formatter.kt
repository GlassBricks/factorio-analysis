package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.BeaconPrototype
import glassbricks.factorio.prototypes.ItemPrototype
import glassbricks.factorio.prototypes.QualityPrototype
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

    fun formatBaseMachine(machine: BaseMachine<*>): String =
        machine.prototype.name + formatQualitySuffix(machine.quality)

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

    fun formatModuleCount(moduleCount: ModuleCount): String {
        if (moduleCount.count == 1) return formatModule(moduleCount.module)
        return formatModule(moduleCount.module) + " x${moduleCount.count}"
    }

    fun formatModule(module: Module): String = formatItem(module)

    fun formatBeaconList(beacons: BeaconList): String =
        beacons.beaconCounts.joinToString(", ") { formatBeaconCount(it) }

    fun formatBeaconCount(beaconCount: BeaconCount): String {
        if (beaconCount.count == 1) return formatBeaconSetup(beaconCount.beaconSetup)
        return formatBeaconSetup(beaconCount.beaconSetup) + " x${beaconCount.count}"
    }

    fun formatBeaconSetup(beaconSetup: BeaconSetup): String =
        "${formatBeacon(beaconSetup.beacon)}[${formatModuleList(beaconSetup.modules)}]"

    fun formatBeaconName(prototype: BeaconPrototype): String = prototype.name
    fun formatBeacon(beacon: Beacon): String = formatBeaconName(beacon.prototype) + formatQualitySuffix(beacon.quality)

    fun formatProcess(process: RecipeOrResource<*>): String = when (process) {
        is Recipe -> formatRecipe(process)
        is Resource -> formatResource(process)
    }

    fun formatRecipe(recipe: Recipe): String = recipe.prototype.name + formatQualitySuffix(recipe.inputQuality)
    fun formatResource(resource: Resource): String = resource.prototype.name
    fun formatQualitySuffix(quality: Quality): String =
        if (quality.level == 0) "" else "(${formatQualityName(quality.prototype)})"

    fun formatQualityName(prototype: QualityPrototype): String = prototype.name

    override fun formatSymbol(symbol: Symbol): String = when (symbol) {
        is Item -> formatItem(symbol)
        is Fluid -> formatFluid(symbol)
        else -> symbol.toString()
    }

    fun formatItemName(prototype: ItemPrototype): String = prototype.name
    fun formatItem(item: Item): String = formatItemName(item.prototype) + formatQualitySuffix(item.quality)
    fun formatFluid(fluid: Fluid): String = fluid.prototype.name

    override fun formatProcess(process: LpProcess): String = when (val process = process.process) {
        is MachineSetup<*> -> formatSetup(process)
        else -> process.toString()
    }

    companion object Default : FactorioRecipesFormatter
}

interface RecipesFirst : FactorioShorthandFormatter {
    override fun formatSetup(setup: MachineSetup<*>): String =
        "${formatProcess(setup.process)} --> ${formatMachine(setup.machine)}"

    override val processComparator: Comparator<in Process>?
        get() = compareBy {
            if (it is MachineSetup<*>) formatProcess(it.process)
            else null
        }

    companion object : RecipesFirst
}
