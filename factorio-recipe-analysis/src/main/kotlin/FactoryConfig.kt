package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.*

@DslMarker
annotation class RecipesConfigDsl

class FactoryConfig(
    override val prototypes: FactorioPrototypes,
    val allProcesses: List<LpProcess>,
) : WithPrototypes

data class ModuleConfig(
    val modules: List<ModuleCount> = emptyList(),
    val fill: Module? = null,
    val beacons: List<BeaconSetup> = emptyList(),
)

data class MachineConfig(
    val machine: AnyCraftingMachine,
    val includeBuildCosts: Boolean,
    val additionalCosts: AmountVector<Symbol>? = null,
)

@RecipesConfigDsl
class MachineConfigScope(
    val prototypes: FactorioPrototypes,
    val machine: CraftingMachine,
) {
    val qualities = sortedSetOf(prototypes.defaultQuality)
    val moduleConfigs = mutableListOf<ModuleConfig>()
    var includeBuildCosts: Boolean = false
    var additionalCosts: AmountVector<Symbol>? = null

    fun emptyModuleConfig() {
        moduleConfigs += ModuleConfig()
    }

    fun moduleConfig(
        vararg modules: WithModuleCount,
        fill: Module? = null,
        beacons: List<BeaconSetup> = emptyList(),
    ) {
        moduleConfigs += ModuleConfig(
            modules.map { it.moduleCount },
            fill = fill,
            beacons = beacons
        )
    }

    internal fun toMachineConfigs(): List<MachineConfig> =
        qualities.flatMap { quality ->
            val machine = machine.withQuality(quality)
            val moduleConfigs = if (moduleConfigs.isEmpty()) listOf(ModuleConfig()) else moduleConfigs
            moduleConfigs.mapNotNull { (modules, fill, beacons) ->
                machine.withModulesOrNull(modules, fill, beacons)
                    ?.let {
                        MachineConfig(it, includeBuildCosts, additionalCosts)
                    }
            }
        }
}

typealias MachineConfigFn = MachineConfigScope.() -> Unit

@RecipesConfigDsl
class RecipeConfig(override val prototypes: FactorioPrototypes, val recipe: Recipe) : WithPrototypes {
    val qualities = sortedSetOf(prototypes.defaultQuality)
    fun allQualities() {
        qualities.addAll(prototypes.qualities)
    }

    var cost: Double = 1.0
    var upperBound: Double = Double.POSITIVE_INFINITY
    var integral: Boolean = false

    var additionalCosts: AmountVector<Symbol>? = null

    private var withQualitiesList: List<Recipe>? = null
    internal fun addCraftingSetups(
        list: MutableList<in LpProcess>,
        machine: MachineConfig,
        config: ResearchConfig,
    ) {
        withQualitiesList = withQualitiesList ?: qualities.mapNotNull { recipe.withQualityOrNull(it) }
        for (quality in withQualitiesList!!) {
            val machineSetup = machine.machine.craftingOrNull(quality, config) ?: continue
            val additionalCosts: AmountVector<Symbol> =
                (machine.additionalCosts ?: emptyVector()) +
                        (this.additionalCosts ?: emptyVector()) +
                        (if (machine.includeBuildCosts) machineSetup.machine.getBuildCost(prototypes) else emptyVector())
            val lpProcess = LpProcess(
                process = machineSetup,
                cost = cost,
                upperBound = upperBound,
                integral = integral,
                additionalCosts = additionalCosts,
            )
            list.add(lpProcess)
        }
    }

    internal val sizeEstimate: Int get() = qualities.size
}
typealias RecipeConfigFn = RecipeConfig.() -> Unit

@RecipesConfigDsl
class FactorioConfigBuilder(override val prototypes: FactorioPrototypes) : WithPrototypes {
    val machines = MachinesScope()
    inline fun machines(block: MachinesScope.() -> Unit) = machines.block()

    @RecipesConfigDsl
    inner class MachinesScope {
        var defaultConfig: MachineConfigFn? = null
        val machineConfigs = mutableMapOf<CraftingMachine, MutableList<MachineConfigFn>>()

        fun default(block: MachineConfigFn) {
            defaultConfig = block
        }

        operator fun String.invoke(block: MachineConfigFn) {
            val machine = this@FactorioConfigBuilder.prototypes.craftingMachines[this] ?: error("Unknown machine $this")
            machine.invoke(block)
        }

        operator fun CraftingMachine.invoke(block: MachineConfigFn) {
            machineConfigs.getOrPut(this, ::ArrayList) += block
        }
    }

    val recipes = RecipesScope()
    inline fun recipes(block: RecipesScope.() -> Unit) = recipes.block()

    @RecipesConfigDsl
    inner class RecipesScope {
        var defaultRecipeConfig: RecipeConfigFn? = null
        val recipeConfigs = mutableMapOf<Recipe, MutableList<RecipeConfigFn>>()
        fun default(block: RecipeConfigFn) {
            defaultRecipeConfig = block
        }

        operator fun String.invoke(block: RecipeConfigFn = {}) {
            val recipe = this@FactorioConfigBuilder.prototypes
                .recipes[this] ?: error("Unknown recipe $this")
            recipe.invoke(block)
        }

        operator fun Recipe.invoke(block: RecipeConfigFn = {}) {
            recipeConfigs.getOrPut(this, ::ArrayList) += block
        }

        fun allOfCategory(category: String, config: RecipeConfigFn = {}) {
            this@FactorioConfigBuilder.prototypes.recipes.values.filter { it.prototype.category.value == category }
                .forEach { recipe ->
                    recipeConfigs.getOrPut(recipe, ::ArrayList).add(config)
                }
        }

        fun allRecycling(config: RecipeConfigFn = {}) {
            allOfCategory("recycling", config)
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

    private fun getAllRecipeConfigs(): List<RecipeConfig> {
        val recipeScope = recipes
        return recipeScope.recipeConfigs.keys.map { recipe ->
            RecipeConfig(prototypes, recipe).apply {
                recipeScope.defaultRecipeConfig?.invoke(this)
                recipeScope.recipeConfigs[recipe]?.forEach { it(this) }
            }
        }
    }

    private fun getAllProcesses(): List<LpProcess> {
        val machines = getAllMachines()
        val configs = getAllRecipeConfigs()
        val sizeEstimate = machines.size * configs.sumOf { it.sizeEstimate } * 1.1
        return buildList(sizeEstimate.toInt()) {
            for (machine in machines) {
                for (config in configs) {
                    config.addCraftingSetups(this, machine, researchConfig)
                }
            }
        }
    }

    fun build(): FactoryConfig = FactoryConfig(prototypes, allProcesses = getAllProcesses())
}

inline fun FactorioPrototypes.factory(block: FactorioConfigBuilder.() -> Unit): FactoryConfig {
    val builder = FactorioConfigBuilder(this)
    builder.block()
    return builder.build()
}
