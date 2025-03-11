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
    val costConfig: CostConfig,
    val machines: Map<BaseMachine<*>, MachineConfig>,
    val recipes: Map<RecipeOrResource<*>, RecipeConfig>,
    val setups: Map<MachineSetup<*>, ProcessConfig>,
    val additionalConfigFn: ((MachineSetup<*>) -> ProcessConfig?)?,
) {

    fun getAllProcesses(): List<RealProcess> {
        val recipesByCategory = recipes.keys.groupBy { it.craftingCategory }
        // cache this because it's an expensive-ish computation we don't want to repeat
        val recipesWithQuality = recipes.mapValues { (recipe, recipeConfig) ->
            recipeConfig.qualities.mapNotNull { recipe.withQualityOrNull(it) }
        }
        return machines.entries.parallelStream().flatMap { (baseMachine, machineConfig) ->
            val machineRecipes = baseMachine.craftingCategories
                .flatMap { recipesByCategory[it].orEmpty() }
                .filter { baseMachine.canProcessInCategory(it) }
            machineConfig.moduleSets.parallelStream().flatMap { moduleSet ->
                val machineWithModules = baseMachine.withModulesOrNull(moduleSet)!!
                val thisRecipes = machineRecipes.filter { it.acceptsModules(moduleSet) }
                machineConfig.qualities.parallelStream().flatMap { machineQuality ->
                    val machineWithQuality = machineWithModules.withQuality(machineQuality)
                    // cache expensive computation
                    val machineCosts =
                        (if (costConfig.includeBuildCosts) machineWithQuality.getBuildCost(prototypes) else emptyVector()) +
                                (if (costConfig.includePowerCosts) vectorOf(ElectricPower to machineWithQuality.powerUsage) else emptyVector())
                    thisRecipes.parallelStream().flatMap { recipe ->
                        val recipeConfig = this@FactoryConfig.recipes[recipe]!!

                        val setup = MachineSetup(baseMachine, recipe)
                        val setupConfig: ProcessConfig =
                            machineConfig.processConfig + recipeConfig.processConfig + setups[setup] +
                                    additionalConfigFn?.invoke(setup)

                        val additionalCosts = machineCosts + setupConfig.additionalCosts
                        recipesWithQuality[recipe]!!.map { recipeWithQuality ->
                            RealProcess(
                                process = MachineProcess(
                                    machineWithQuality,
                                    recipeWithQuality,
                                    research,
                                    skipCanProcessCheck = true
                                ),
                                variableConfig = setupConfig.variableConfig(),
                                additionalCosts = additionalCosts,
                                costVariableConfig = setupConfig.costVariableConfig
                            )
                        }.stream()
                    }
                }
            }
        }
            .toList()
    }
}

data class ProcessConfig(
    val additionalCosts: Vector<Symbol>,
    val costVariableConfig: VariableConfig?,
    val lowerBound: Double,
    val upperBound: Double,
    val cost: Double,
    val variableType: VariableType,
) {
    operator fun plus(other: ProcessConfig?): ProcessConfig = if (other == null) this else ProcessConfig(
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

data class CostConfig(
    val includePowerCosts: Boolean = false,
    val includeBuildCosts: Boolean = false,
)

data class MachineConfig(
    val processConfig: ProcessConfig,
    val qualities: Set<Quality>,
    val moduleSets: List<ModuleSet>,
)

data class RecipeConfig(
    val processConfig: ProcessConfig,
    val qualities: Set<Quality>,
    val filters: List<(AnyMachine<*>, RecipeOrResource<*>) -> Boolean>,
)

private inline fun <T, R> Iterable<T>.groupByMulti(keys: (T) -> Iterable<R>): Map<R, List<T>> =
    buildMap<_, MutableList<T>> {
        for (item in this@groupByMulti) {
            for (key in keys(item)) {
                getOrPut(key, ::mutableListOf).add(item)
            }
        }
    }
