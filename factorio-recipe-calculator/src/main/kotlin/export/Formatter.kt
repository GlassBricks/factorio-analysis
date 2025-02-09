package glassbricks.factorio.recipes.export

import glassbricks.factorio.prototypes.*
import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.Process
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.recipelp.RecipeLpFormatter

interface FactorioRecipesFormatter : RecipeLpFormatter {
    fun formatSetup(setup: MachineSetup<*>): String =
        "${formatMachine(setup.machine)} --> ${formatRecipeOrResource(setup.recipe)}"

    fun formatMachine(machine: AnyMachine<*>): String = when (machine) {
        is BaseMachine<*> -> formatBaseMachine(machine)
        is MachineWithModules -> formatMachineWithModules(machine)
        else -> error("Unknown machine type: $machine")
    }

    fun formatEntityName(prototype: EntityPrototype): String = prototype.name
    fun formatBaseMachine(machine: BaseMachine<*>): String =
        formatEntityName(machine.prototype) + formatQualityQualifier(machine.quality)

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

    fun formatBeaconName(prototype: BeaconPrototype): String = formatEntityName(prototype)
    fun formatBeacon(beacon: Beacon): String =
        formatBeaconName(beacon.prototype) + formatQualityQualifier(beacon.quality)

    fun formatRecipeOrResource(process: RecipeOrResource<*>): String = when (process) {
        is Recipe -> formatRecipe(process)
        is Resource -> formatResource(process)
    }

    fun formatResourceOrRecipeName(prototype: Prototype) = when (prototype) {
        is RecipePrototype -> formatRecipeName(prototype)
        is ResourceEntityPrototype -> formatResourceName(prototype)
        else -> error("Not a recipe or resource: $prototype")
    }

    fun formatRecipeName(prototype: RecipePrototype): String = prototype.name
    fun formatRecipe(recipe: Recipe): String =
        formatRecipeName(recipe.prototype) + formatQualityQualifier(recipe.inputQuality)

    fun formatResourceName(prototype: ResourceEntityPrototype): String = prototype.name
    fun formatResource(resource: Resource): String = formatResourceName(resource.prototype)
    fun formatQualityQualifier(quality: Quality): String =
        if (quality.level == 0) "" else "(${formatQualityName(quality.prototype)})"

    fun formatQualityName(prototype: QualityPrototype): String = prototype.name

    override fun formatSymbol(symbol: Symbol): String = when (symbol) {
        is Item -> formatItem(symbol)
        is Fluid -> formatFluid(symbol)
        else -> symbol.toString()
    }

    fun formatItemName(prototype: ItemPrototype): String = prototype.name
    fun formatItem(item: Item): String = formatItemName(item.prototype) + formatQualityQualifier(item.quality)
    fun formatFluid(fluid: Fluid): String = fluid.prototype.name

    override fun formatProcess(process: Process): String = when (process) {
        is MachineSetup<*> -> formatSetup(process)
        else -> process.toString()
    }

    fun formatMachineSetupUsage(setup: MachineSetup<*>, rate: Double): String = defaultNumberFormat(rate)

    override fun formatRealProcessUsage(process: Process, rate: Double): String =
        if (process is MachineSetup<*>) formatMachineSetupUsage(process, rate)
        else super.formatRealProcessUsage(process, rate)

    companion object Default : FactorioRecipesFormatter
}

interface RecipesFirst : FactorioShorthandFormatter {
    override fun formatSetup(setup: MachineSetup<*>): String =
        "${formatRecipeOrResource(setup.recipe)} --> ${formatMachine(setup.machine)}"

    override val processComparator: Comparator<in Process>?
        get() = compareBy {
            if (it is MachineSetup<*>) formatRecipeOrResource(it.recipe)
            else null
        }

    companion object : RecipesFirst
}
