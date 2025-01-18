package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.Input
import glassbricks.recipeanalysis.Output

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

class FactoryConfig(val allCraftingSetups: List<CraftingSetup>)

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

class RecipeConfig(
    val prototypes: FactorioPrototypes,
    val recipe: Recipe,
) {
    val qualities = sortedSetOf(prototypes.defaultQuality)
    private var withQualitiesList: List<Recipe>? = null

    fun toCraftingSetups(
        machine: AnyCraftingMachine,
        config: ResearchConfig,
    ) = buildList { addCraftingSetups(this, machine, config) }

    internal fun addCraftingSetups(
        list: MutableList<CraftingSetup>,
        machine: AnyCraftingMachine,
        config: ResearchConfig,
    ) {
        withQualitiesList = withQualitiesList ?: qualities.map { recipe.withQuality(it) }
        for (quality in withQualitiesList!!) {
            val element = machine.craftingOrNull(quality, config)
            element?.let { list.add(it) }
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

    private fun getAllCraftingSetups(): List<CraftingSetup> {
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

    fun build(): FactoryConfig {
        return FactoryConfig(allCraftingSetups = getAllCraftingSetups())
    }
}

inline fun factoryConfig(prototypes: FactorioPrototypes, block: FactorioConfigBuilder.() -> Unit): FactoryConfig {
    val builder = FactorioConfigBuilder(prototypes)
    builder.block()
    return builder.build()
}

class ProblemConfig(
    val factoryConfig: FactoryConfig,
    val inputs: List<Input>,
    val outputs: List<Output>,
)
