package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.Process

@DslMarker
annotation class RecipesConfigDsl

/*
recipeConfig(SpaceAge) {
    machines {
        addAllMachines()
        default {
            +moduleConfig(
                prod1, *,
                fill=prod2,
            ) + beacon(prod1*2)*8
        }
        "recycler" {
            qualities += uncommon
            +moduleConfig(...)
        }
    }
    recipes {
        // todo
    }
}
 */

class FactoryConfig(
    override val prototypes: FactorioPrototypes,
    val allProcesses: List<Process>,
) : WithPrototypes

data class ModuleConfig(
    val modules: List<ModuleCount> = emptyList(),
    val fill: Module? = null,
    val beacons: List<BeaconSetup> = emptyList(),
)

@RecipesConfigDsl
class MachineConfig(
    val prototypes: FactorioPrototypes,
    val machine: CraftingMachine,
) {
    val qualities = sortedSetOf(prototypes.defaultQuality)
    val moduleConfigs = mutableListOf<ModuleConfig>()

    fun emptyModuleConfig() {
        moduleConfigs += ModuleConfig()
    }

    fun moduleConfig(
        vararg modules: WithModuleCount,
        fill: Module? = null,
        beacons: List<BeaconSetup> = emptyList(),
    ) {
        moduleConfigs += ModuleConfig(
            modules.map { it.moduleCount }, fill = fill, beacons = beacons
        )
    }

    internal fun toMachines(): List<AnyCraftingMachine> =
        qualities.flatMap { quality ->
            val machine = machine.withQuality(quality)
            moduleConfigs.mapNotNull { (modules, fill, beacons) ->
                machine.withModulesOrNull(modules, fill, beacons)
            }
        }
}

typealias MachineConfigFn = MachineConfig.() -> Unit

class RecipeConfig(override val prototypes: FactorioPrototypes, val recipe: Recipe) : WithPrototypes {
    val qualities = sortedSetOf(prototypes.defaultQuality)
    fun allQualities() {
        qualities.addAll(prototypes.qualities)
    }

    var cost: Double = 1.0
    var upperBound: Double = Double.POSITIVE_INFINITY
    var integral: Boolean = false

    private var withQualitiesList: List<Recipe>? = null
    internal fun addCraftingSetups(
        list: MutableList<in Process>,
        machine: AnyCraftingMachine,
        config: ResearchConfig,
    ) {
        withQualitiesList = withQualitiesList ?: qualities.mapNotNull { recipe.withQualityOrNull(it) }
        for (quality in withQualitiesList!!) {
            machine.craftingOrNull(quality, config)
                ?.let { Process(process = it, cost = cost, upperBound = upperBound, integral = integral) }
                ?.let { list.add(it) }
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
        var defaultConfig: MachineConfigFn? = {
            emptyModuleConfig()
        }
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

    private fun getAllMachines(): List<AnyCraftingMachine> {
        val machineScope = machines
        return machineScope.machineConfigs.keys.flatMap {
            MachineConfig(prototypes, it).apply {
                machineScope.defaultConfig?.invoke(this)
                machineScope.machineConfigs[it]?.forEach { it(this) }
            }.toMachines()
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

    private fun getAllProcesses(): List<Process> {
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
