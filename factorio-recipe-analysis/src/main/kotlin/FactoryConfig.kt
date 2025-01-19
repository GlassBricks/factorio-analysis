package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.RecipeCategoryID
import glassbricks.recipeanalysis.*

@DslMarker
annotation class RecipesConfigDsl

class FactoryConfig(
    override val prototypes: FactorioPrototypes,
    val allProcesses: List<LpProcess>,
) : WithFactorioPrototypes

data class ModuleConfig(
    val modules: List<WithModuleCount> = emptyList(),
    val fill: Module? = null,
    val beacons: List<WithBeaconCount> = emptyList(),
)

data class MachineConfig(
    val machine: AnyCraftingMachine,
    val includeBuildCosts: Boolean,
    val additionalCosts: AmountVector<Symbol>,
)

class MachineConfigScope(
    val prototypes: FactorioPrototypes,
    val machine: CraftingMachine,
) {
    val qualities = sortedSetOf(prototypes.defaultQuality)

    var includeBuildCosts: Boolean = false
    fun includeBuildCosts() {
        includeBuildCosts = true
    }

    var additionalCosts: AmountVector<Symbol> = emptyVector()

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
class RecipeConfigScope(override val prototypes: FactorioPrototypes, val recipe: Recipe) : WithFactorioPrototypes {
    val qualities = sortedSetOf(prototypes.defaultQuality)
    fun allQualities() {
        qualities.addAll(prototypes.qualities)
    }

    var cost: Double = 1.0
    var upperBound: Double = Double.POSITIVE_INFINITY
    var integral: Boolean = false

    var additionalCosts: AmountVector<Symbol> = emptyVector()

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
                machine.additionalCosts + this.additionalCosts +
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
typealias RecipeConfigFn = RecipeConfigScope.() -> Unit

@RecipesConfigDsl
class FactorioConfigBuilder(override val prototypes: FactorioPrototypes) : WithFactorioPrototypes {
    val machines = MachinesScope()
    inline fun machines(block: MachinesScope.() -> Unit) = machines.block()

    @RecipesConfigDsl
    inner class MachinesScope : WithFactorioPrototypes by this@FactorioConfigBuilder {
        var defaultConfig: MachineConfigFn? = null
        val machineConfigs = mutableMapOf<CraftingMachine, MutableList<MachineConfigFn>>()

        fun default(block: MachineConfigFn) {
            defaultConfig = block
        }

        operator fun CraftingMachine.invoke(block: MachineConfigFn? = null) {
            val list = machineConfigs.getOrPut(this, ::ArrayList)
            if (block != null) list += block
        }

        operator fun String.invoke(block: MachineConfigFn) {
            craftingMachine(this)(block)
        }

    }

    val recipes = RecipesScope()
    inline fun recipes(block: RecipesScope.() -> Unit) = recipes.block()

    @RecipesConfigDsl
    inner class RecipesScope : WithFactorioPrototypes by this@FactorioConfigBuilder {
        var defaultRecipeConfig: RecipeConfigFn? = null
        val recipeConfigs = mutableMapOf<Recipe, MutableList<RecipeConfigFn>>()
        fun default(block: RecipeConfigFn) {
            defaultRecipeConfig = block
        }

        operator fun Recipe.invoke(block: RecipeConfigFn? = null) {
            val list = recipeConfigs.getOrPut(this, ::ArrayList)
            if (block != null) list += block
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
        val configsByCategory = configs.groupBy { it.recipe.prototype.category }
        val sizeEstimate = machines.size * configs.sumOf { it.sizeEstimate } * 1.1
        return buildList(sizeEstimate.toInt()) {
            for (machine in machines) {
                for (category in machine.machine.prototype.crafting_categories) {
                    val categoryConfigs = configsByCategory[category] ?: continue
                    for (config in categoryConfigs) {
                        config.addCraftingSetups(this, machine, researchConfig)
                    }
                }
            }
        }
    }

    fun build(): FactoryConfig = FactoryConfig(prototypes, allProcesses = getAllProcesses())
}

inline fun WithFactorioPrototypes.factory(block: FactorioConfigBuilder.() -> Unit): FactoryConfig {
    val builder = FactorioConfigBuilder(prototypes)
    builder.block()
    return builder.build()
}
