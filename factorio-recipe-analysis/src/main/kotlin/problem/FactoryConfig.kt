package glassbricks.factorio.recipes.problem

import glassbricks.factorio.prototypes.CraftingMachinePrototype
import glassbricks.factorio.prototypes.MiningDrillPrototype
import glassbricks.factorio.prototypes.RecipeCategoryID
import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.Vector
import glassbricks.recipeanalysis.emptyVector
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.VariableConfigBuilder
import glassbricks.recipeanalysis.lp.VariableType
import glassbricks.recipeanalysis.plus
import glassbricks.recipeanalysis.recipelp.LpProcess

@DslMarker
annotation class RecipesConfigDsl

class FactoryConfig(
    override val prototypes: FactorioPrototypes,
    val allProcesses: List<LpProcess>,
) : WithFactorioPrototypes

data class MachineConfig(
    val machine: AnyMachine<*>,
    val includeBuildCosts: Boolean,
    val additionalCosts: Vector<Symbol>,
    val variableConfig: VariableConfig,
    val outputVariableConfig: VariableConfig?,
)

class MachineConfigScope(
    val prototypes: FactorioPrototypes,
    val machine: BaseMachine<*>,
) {
    val qualities = sortedSetOf(prototypes.defaultQuality)

    var includeBuildCosts: Boolean = false
    fun includeBuildCosts() {
        includeBuildCosts = true
    }

    var additionalCosts: Vector<Symbol> = emptyVector()
    var outputVariableConfig: VariableConfigBuilder? = null

    /**
     * Cost will either be applied to recipe variable or output variable, depending on if outputVariableConfig is set.
     */
    var cost: Double = DefaultWeights.RECIPE_COST
    var lowerBound: Double = 0.0
    var upperBound: Double = Double.POSITIVE_INFINITY
    var type: VariableType = VariableType.Continuous

    /** When evaluating the _cost_ of using this recipe, the number of machines will be rounded up. */
    fun integralCost() {
        outputVariableConfig = VariableConfigBuilder(type = VariableType.Integer)
    }

    /** When evaluating the _cost_ of using this recipe, the number of machines will be at minimum this value. */
    fun semiContinuousCost(lowerBound: Double = 0.0) {
        outputVariableConfig =
            VariableConfigBuilder(type = VariableType.SemiContinuous, lowerBound = lowerBound, hint = 1.0)
    }

    val moduleConfigs = mutableListOf<ModuleConfig>()
    fun emptyModuleConfig() {
        moduleConfigs += ModuleConfig()
    }

    fun moduleConfig(
        vararg modules: WithModuleCount,
        fill: Module? = null,
        beacons: List<WithBeaconCount> = emptyList(),
    ) {
        moduleConfigs += ModuleConfig(
            modules.asList(),
            fill = fill,
            beacons = beacons,
        )
    }

    internal fun toMachineConfigs(): List<MachineConfig> {
        val varConfigCost = if (outputVariableConfig != null) 0.0 else cost
        val variableConfig = VariableConfig(
            lowerBound = lowerBound,
            upperBound = upperBound,
            type = type,
            cost = varConfigCost,
        )
        outputVariableConfig?.cost = cost
        val outVarConfig = outputVariableConfig?.build()
        return qualities.flatMap { quality ->
            val machine = machine.withQuality(quality)
            val moduleConfigs = if (moduleConfigs.isEmpty()) listOf(ModuleConfig()) else moduleConfigs
            moduleConfigs.mapNotNull { (modules, fill, beacons) ->
                machine.withModulesOrNull(modules, fill, beacons)?.let {
                    MachineConfig(
                        machine = it,
                        includeBuildCosts = includeBuildCosts,
                        additionalCosts = additionalCosts,
                        variableConfig = variableConfig,
                        outputVariableConfig = outVarConfig,
                    )
                }
            }
        }
    }

}

typealias MachineConfigFn = MachineConfigScope.() -> Unit

@RecipesConfigDsl
class RecipeConfigScope(override val prototypes: FactorioPrototypes, val process: RecipeOrResource<*>) :
    WithFactorioPrototypes {
    val qualities = sortedSetOf(prototypes.defaultQuality)
    fun allQualities() {
        qualities.addAll(prototypes.qualities)
    }

    fun setQualities(vararg qualities: Quality) {
        this.qualities.clear()
        this.qualities.addAll(qualities)
    }

    var additionalCosts: Vector<Symbol> = emptyVector()

    val filters: MutableList<(MachineConfig) -> Boolean> = mutableListOf()

    private var withQualitiesList: List<RecipeOrResource<*>>? = null
    internal fun addCraftingSetups(
        list: MutableList<in LpProcess>,
        machine: MachineConfig,
        config: ResearchConfig,
    ) {
        if (filters.any { !it(machine) }) return
        withQualitiesList = withQualitiesList ?: qualities.mapNotNull { process.withQualityOrNull(it) }
        for (quality in withQualitiesList!!) {
            val machineSetup = machine.machine.craftingOrNullCast(quality, config) ?: continue
            val additionalCosts: Vector<Symbol> =
                machine.additionalCosts + this.additionalCosts +
                        (if (machine.includeBuildCosts) machineSetup.machine.getBuildCost(prototypes) else emptyVector())
            val lpProcess = LpProcess(
                process = machineSetup,
                additionalCosts = additionalCosts,
                costVariableConfig = machine.outputVariableConfig,
                variableConfig = machine.variableConfig,
            )
            list.add(lpProcess)
        }
    }

    internal val sizeEstimate: Int get() = qualities.size
}
typealias RecipeConfigFn = RecipeConfigScope.() -> Unit

@RecipesConfigDsl
class FactoryConfigBuilder(override val prototypes: FactorioPrototypes) : WithFactorioPrototypes {
    val machines = MachinesScope()
    inline fun machines(block: MachinesScope.() -> Unit) = machines.block()

    @RecipesConfigDsl
    inner class MachinesScope : WithFactorioPrototypes by this@FactoryConfigBuilder {
        var defaultConfig: MachineConfigFn? = null
        val machineConfigs = mutableMapOf<BaseMachine<*>, MutableList<MachineConfigFn>>()

        fun default(block: MachineConfigFn) {
            defaultConfig = block
        }

        operator fun BaseMachine<*>.invoke(block: MachineConfigFn? = null) {
            val list = machineConfigs.getOrPut(this, ::ArrayList)
            if (block != null) list += block
        }

        operator fun String.invoke(block: MachineConfigFn) {
            machine(this)(block)
        }
    }

    val recipes = RecipesScope()
    inline fun recipes(block: RecipesScope.() -> Unit) = recipes.block()

    @RecipesConfigDsl
    inner class RecipesScope : WithFactorioPrototypes by this@FactoryConfigBuilder {
        var defaultRecipeConfig: RecipeConfigFn? = null
        val recipeConfigs = mutableMapOf<RecipeOrResource<*>, MutableList<RecipeConfigFn>>()
        fun default(block: RecipeConfigFn) {
            defaultRecipeConfig = block
        }

        operator fun RecipeOrResource<*>.invoke(block: RecipeConfigFn? = null) {
            val list = recipeConfigs.getOrPut(this, ::ArrayList)
            if (block != null) list += block
        }

        operator fun Item.invoke(block: RecipeConfigFn? = null) {
            prototypes.recipeOf(this)(block)
        }

        operator fun String.invoke(block: RecipeConfigFn? = null) {
            recipe(this)(block)
        }

        fun allRecipes(config: RecipeConfigFn? = null) {
            for (recipe in prototypes.recipes.values) {
                recipe(config)
            }
        }

        fun allOfCategory(category: String, config: RecipeConfigFn?) {
            prototypes.recipesByCategory[RecipeCategoryID(category)]?.forEach { recipe ->
                recipe(config)
            }
        }

        fun allRecycling(config: RecipeConfigFn = {}) {
            allOfCategory("recycling", config)
        }

        fun mining(
            vararg resources: String,
            config: RecipeConfigFn? = null,
        ) {
            for (resource in resources) {
                resource(resource)(config)
            }
        }

        fun remove(recipe: RecipeOrResource<*>) {
            recipeConfigs.remove(recipe)
        }

        fun remove(item: Item) {
            remove(prototypes.recipeOf(item))
        }
    }

    var researchConfig: ResearchConfig = ResearchConfig()

    private fun getAllMachines(): List<MachineConfig> {
        val machineScope = machines
        return machineScope.machineConfigs.keys.flatMap {
            MachineConfigScope(prototypes, it).apply {
                machineScope.defaultConfig?.invoke(this)
                machineScope.machineConfigs[it]?.forEach { it(this) }
            }.toMachineConfigs()
        }
    }

    private fun getAllRecipeConfigs(): List<RecipeConfigScope> {
        val recipeScope = recipes
        return recipeScope.recipeConfigs.keys.map { recipe ->
            RecipeConfigScope(prototypes, recipe).apply {
                recipeScope.defaultRecipeConfig?.invoke(this)
                recipeScope.recipeConfigs[recipe]?.forEach { it(this) }
            }
        }
    }

    private fun getAllProcesses(): List<LpProcess> {
        val machines = getAllMachines()
        val configs = getAllRecipeConfigs()
        val (recipes, mining) = configs.partition { it.process is Recipe }
        val recipeConfigs = recipes.groupBy { (it.process as Recipe).prototype.category }
        val miningConfigs = mining.groupBy { (it.process as Resource).prototype.category }
        return buildList {
            for (machine in machines) {
                val prototype = machine.machine.prototype
                if (prototype is CraftingMachinePrototype) {
                    for (category in prototype.crafting_categories) {
                        val categoryConfigs = recipeConfigs[category] ?: continue
                        for (config in categoryConfigs) {
                            config.addCraftingSetups(this, machine, researchConfig)
                        }
                    }
                } else if (prototype is MiningDrillPrototype) {
                    for (category in prototype.resource_categories) {
                        val categoryConfigs = miningConfigs[category] ?: continue
                        for (config in categoryConfigs) {
                            config.addCraftingSetups(this, machine, researchConfig)
                        }
                    }
                } else error("Unknown machine type: $prototype")
            }
        }
    }

    fun build(): FactoryConfig = FactoryConfig(prototypes, allProcesses = getAllProcesses())
}

inline fun WithFactorioPrototypes.factory(block: FactoryConfigBuilder.() -> Unit): FactoryConfig {
    val builder = FactoryConfigBuilder(prototypes)
    builder.block()
    return builder.build()
}
