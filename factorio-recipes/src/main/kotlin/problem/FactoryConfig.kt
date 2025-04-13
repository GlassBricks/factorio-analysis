package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.Vector
import glassbricks.recipeanalysis.buildVector
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.VariableType
import glassbricks.recipeanalysis.plus
import glassbricks.recipeanalysis.recipelp.RealProcess

interface Factory {
    val prototypes: FactorioPrototypes

    fun machinesUsed(): Set<BaseMachine<*>>
    fun recipesUsed(): Set<PseudoRecipe>
    fun getAllProcesses(): List<RealProcess>

    /** Filter recipes if it would actually reduce the number of processes */
    fun filterMachineRecipes(predicate: (MachineRecipe<*>) -> Boolean): Factory
}

data class CostConfig(
    /**
     * If true, adds the power usage to additionalRate
     */
    val includePowerUsage: Boolean = false,
    /**
     * If true, adds the build cost of the machine ([AnyMachine.getBuildCost]) to additionalCosts.
     */
    val includeBuildCosts: Boolean = false,
    /**
     * If true, adds the exact machine setup used ([MachineSymbol]) to additionalCosts.
     */
    val includeMachineCount: Boolean = false,
)

data class MachineConfig(
    val processConfig: ProcessConfig,
    val qualities: Set<Quality>,
    val moduleSets: List<ModuleSet>,
    val filters: List<SetupPredicate>,
)

data class RecipeConfig(
    val processConfig: ProcessConfig,
    val qualities: Set<Quality>,
    val filters: List<SetupPredicate>,
)

data class FactoryConfig(
    override val prototypes: FactorioPrototypes,
    val research: ResearchConfig,
    val costConfig: CostConfig,
    val machines: Map<BaseMachine<*>, MachineConfig>,
    val recipes: Map<MachineRecipe<*>, RecipeConfig>,
    val setups: Map<MachineSetup<*>, ProcessConfig>,
    val additionalConfigFn: ((MachineSetup<*>) -> ProcessConfig?)?,
    val filters: List<SetupPredicate>,
) : Factory {

    override fun recipesUsed(): Set<PseudoRecipe> = buildSet {
        addAll(recipes.keys)
        if (costConfig.includePowerUsage) {
            addAll(prototypes.getFuelUsageRecipes())
        }
    }

    override fun machinesUsed(): Set<BaseMachine<*>> = machines.keys
    override fun getAllProcesses(): List<RealProcess> = buildList {
        addAll(getMachineProcesses())
        if (costConfig.includePowerUsage) {
            addAll(prototypes.getFuelUsageRecipes().map { RealProcess(it) })
        }
    }

    private fun getMachineProcesses(): List<RealProcess> {
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
                    val machineCosts = buildVector {
                        if (costConfig.includeBuildCosts) this += machineWithQuality.getBuildCost(prototypes)
                        if (costConfig.includeMachineCount) inc(MachineSymbol(machineWithQuality), 1.0)
                    }
                    thisRecipes.parallelStream().flatMap { recipe ->
                        val recipeConfig = this@FactoryConfig.recipes[recipe]!!

                        val setup = MachineSetup(baseMachine, recipe)
                        val setupConfig: ProcessConfig =
                            machineConfig.processConfig + recipeConfig.processConfig + setups[setup] +
                                    additionalConfigFn?.invoke(setup)

                        val additionalCosts = machineCosts + setupConfig.additionalCosts
                        recipesWithQuality[recipe]!!.mapNotNull { recipeWithQuality ->
                            if (!(machineConfig.filters.all { it(machineWithQuality, recipeWithQuality) } &&
                                        recipeConfig.filters.all { it(machineWithQuality, recipeWithQuality) } &&
                                        this@FactoryConfig.filters.all { it(machineWithQuality, recipeWithQuality) }
                                        )) return@mapNotNull null

                            RealProcess(
                                process = MachineProcess(
                                    machineWithQuality,
                                    recipeWithQuality,
                                    costConfig.includePowerUsage,
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
        }.toList()
    }

    override fun filterMachineRecipes(predicate: (MachineRecipe<*>) -> Boolean): Factory {
        // note: pseudo recipes are not filtered
        return copy(recipes = recipes.filterKeys(predicate))
    }
}

class FactorySum(val configs: List<Factory>) : Factory {
    override val prototypes: FactorioPrototypes get() = configs.first().prototypes

    init {
        require(configs.all { it.prototypes == configs.first().prototypes }) {
            "All factory configs must use the same prototypes"
        }
    }

    override fun machinesUsed(): Set<BaseMachine<*>> = configs.flatMap { it.machinesUsed() }.toSet()
    override fun recipesUsed(): Set<PseudoRecipe> = configs.flatMap { it.recipesUsed() }.toSet()
    override fun getAllProcesses(): List<RealProcess> = configs.flatMap { it.getAllProcesses() }
    override fun filterMachineRecipes(predicate: (MachineRecipe<*>) -> Boolean): Factory =
        FactorySum(configs.map { it.filterMachineRecipes(predicate) })
}

operator fun Factory.plus(other: Factory): Factory {
    fun Factory.getList(): List<Factory> = when (this) {
        is FactorySum -> configs
        else -> listOf(this)
    }
    return FactorySum(this.getList() + other.getList())
}

typealias SetupPredicate = (AnyMachine<*>, MachineRecipe<*>) -> Boolean

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
