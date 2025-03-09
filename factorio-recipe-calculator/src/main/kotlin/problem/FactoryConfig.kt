package glassbricks.factorio.recipes.problem

import glassbricks.factorio.prototypes.*
import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.VariableType
import glassbricks.recipeanalysis.recipelp.RealProcess

data class FactoryConfig(
    val prototypes: FactorioPrototypes,
    val research: ResearchConfig,
    val machines: Map<BaseMachine<*>, MachineConfig>,
    val recipes: Map<RecipeOrResource<*>, RecipeConfig>,
    val setups: Map<MachineSetup<*>, ProcessConfig>,
    val additionalConfigFn: ((MachineSetup<*>) -> ProcessConfig?)?,
) {

    fun getAllProcesses(): List<RealProcess> {
        val recipesByCategory = recipes.keys.groupBy<_, Any> {
            val prototype = it.prototype
            when (prototype) {
                is RecipePrototype -> prototype.category
                is ResourceEntityPrototype -> prototype.category
                else -> error("Unknown prototype type: $prototype")
            }
        }
        return machines.entries.parallelStream().flatMap { (machine, machineConfig) ->
            val categories = when (val p: MachinePrototype = machine.prototype) {
                is CraftingMachinePrototype -> p.crafting_categories
                is MiningDrillPrototype -> p.resource_categories
                else -> error("Unknown machine type: $p")
            }
            categories.parallelStream()
                .flatMap { cat -> recipesByCategory[cat].orEmpty().stream() }
                .filter { recipe -> machine.canProcess(recipe) }
                .flatMap { recipe ->
                    val recipeConfig = recipes[recipe]!!

                    val setup = MachineSetup(machine, recipe)
                    val setupConfig: ProcessConfig =
                        machineConfig.processConfig + recipeConfig.processConfig + setups[setup] +
                                additionalConfigFn?.invoke(setup)

                    val installedMachines =
                        machineConfig.moduleSets.filter { recipe.acceptsModules(it) }
                            .mapNotNull { moduleSet -> machine.withModulesOrNull(moduleSet) }
                    val machinesWithQuality = installedMachines.flatMap { machineWithModules ->
                        machineConfig.qualities.map { machineWithModules.withQuality(it) }
                    }
                    val recipesWithQuality = recipeConfig.qualities.mapNotNull { recipe.withQualityOrNull(it) }

                    machinesWithQuality.stream().flatMap { machineWithQuality ->
                        val additionalCosts = buildVector {
                            this += setupConfig.additionalCosts
                            if (setupConfig.includeBuildCosts) {
                                this += machineWithQuality.getBuildCost(prototypes)
                            }
                            if (setupConfig.includePowerCosts) {
                                this[ElectricPower] = machineWithQuality.powerUsage
                            }
                        }

                        recipesWithQuality.stream().map { recipeWithQuality ->
                            RealProcess(
                                process = MachineProcess(machineWithQuality, recipeWithQuality, research),
                                variableConfig = setupConfig.variableConfig(),
                                additionalCosts = additionalCosts,
                                costVariableConfig = setupConfig.costVariableConfig
                            )
                        }
                    }
                }
        }
            .toList()
    }
}

data class ProcessConfig(
    val includeBuildCosts: Boolean,
    val includePowerCosts: Boolean,
    val additionalCosts: Vector<Symbol>,
    val costVariableConfig: VariableConfig?,
    val lowerBound: Double,
    val upperBound: Double,
    val cost: Double,
    val variableType: VariableType,
) {
    operator fun plus(other: ProcessConfig?): ProcessConfig = if (other == null) this else ProcessConfig(
        includeBuildCosts = includeBuildCosts || other.includeBuildCosts,
        includePowerCosts = includePowerCosts || other.includePowerCosts,
        additionalCosts = additionalCosts + other.additionalCosts,
        costVariableConfig = costVariableConfig ?: other.costVariableConfig,
        lowerBound = maxOf(lowerBound, other.lowerBound),
        upperBound = minOf(upperBound, other.upperBound),
        cost = cost + other.cost,
        variableType = when {
            variableType == VariableType.Integer || other.variableType == VariableType.Integer -> VariableType.Integer
            variableType == VariableType.SemiContinuous || other.variableType == VariableType.SemiContinuous -> VariableType.SemiContinuous
            else -> VariableType.Continuous
        }
    )

    fun variableConfig(): VariableConfig = VariableConfig(
        lowerBound = lowerBound,
        upperBound = upperBound,
        type = variableType,
        cost = cost,
    )
}

data class MachineConfig(
    val processConfig: ProcessConfig,
    val qualities: Set<Quality>,
    val moduleSets: List<ModuleSet>,
)

data class RecipeConfig(
    val processConfig: ProcessConfig,
    val qualities: Set<Quality>,
)
