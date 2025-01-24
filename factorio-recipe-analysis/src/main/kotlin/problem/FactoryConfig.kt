package glassbricks.factorio.recipes.problem

import glassbricks.factorio.prototypes.CraftingMachinePrototype
import glassbricks.factorio.prototypes.MiningDrillPrototype
import glassbricks.factorio.prototypes.RecipeCategoryID
import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.*

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
    val integral: Boolean,
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
    var integral = false

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
                        MachineConfig(
                            machine = it,
                            includeBuildCosts = includeBuildCosts,
                            additionalCosts = additionalCosts,
                            integral = integral
                        )
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

    var cost: Double = 1.0
    var upperBound: Double = Double.POSITIVE_INFINITY
    var integral: Boolean = false

    var additionalCosts: Vector<Symbol> = emptyVector()

    private var withQualitiesList: List<RecipeOrResource<*>>? = null
    internal fun addCraftingSetups(
        list: MutableList<in LpProcess>,
        machine: MachineConfig,
        config: ResearchConfig,
    ) {
        withQualitiesList = withQualitiesList ?: qualities.mapNotNull { process.withQualityOrNull(it) }
        for (quality in withQualitiesList!!) {
            val machineSetup = machine.machine.craftingOrNullCast(quality, config) ?: continue
            val additionalCosts: Vector<Symbol> =
                machine.additionalCosts + this.additionalCosts +
                        (if (machine.includeBuildCosts) machineSetup.machine.getBuildCost(prototypes) else emptyVector())
            val lpProcess = LpProcess(
                process = machineSetup,
                cost = cost,
                upperBound = upperBound,
                integral = machine.integral || this.integral,
                additionalCosts = additionalCosts,
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
        val sizeEstimate = machines.size * configs.sumOf { it.sizeEstimate } * 1.1
        return buildList(sizeEstimate.toInt()) {
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
