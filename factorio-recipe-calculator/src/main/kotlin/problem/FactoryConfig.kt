package glassbricks.factorio.recipes.problem

import glassbricks.factorio.prototypes.CraftingMachinePrototype
import glassbricks.factorio.prototypes.MiningDrillPrototype
import glassbricks.factorio.prototypes.RecipeCategoryID
import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.Vector
import glassbricks.recipeanalysis.emptyVector
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.VariableType
import glassbricks.recipeanalysis.plus
import glassbricks.recipeanalysis.recipelp.RealProcess

@DslMarker
annotation class FactoryConfigDsl

class FactoryConfig(
    override val prototypes: FactorioPrototypes,
    val allProcesses: List<RealProcess>,
) : WithFactorioPrototypes

data class SetupConfig(
    val includeBuildCosts: Boolean,
    val additionalCosts: Vector<Symbol>,
    val costVariableConfig: VariableConfig?,
    val lowerBound: Double,
    val upperBound: Double,
    val cost: Double,
    val variableType: VariableType,
) {
    operator fun plus(other: SetupConfig): SetupConfig = SetupConfig(
        includeBuildCosts = includeBuildCosts || other.includeBuildCosts,
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

interface ISetupConfigBuilder {
    var includeBuildCosts: Boolean
    var additionalCosts: Vector<Symbol>
    var outputVariableConfig: VariableConfig?

    var lowerBound: Double
    var upperBound: Double
    var cost: Double
    var type: VariableType

    fun includeBuildCosts() {
        includeBuildCosts = true
    }
}

fun ISetupConfigBuilder.build(): SetupConfig = SetupConfig(
    includeBuildCosts = includeBuildCosts,
    additionalCosts = additionalCosts,
    costVariableConfig = outputVariableConfig,
    lowerBound = lowerBound,
    upperBound = upperBound,
    cost = cost,
    variableType = type,
)

class SetupConfigBuilder : ISetupConfigBuilder {
    override var includeBuildCosts: Boolean = false
    override var additionalCosts: Vector<Symbol> = emptyVector()
    override var outputVariableConfig: VariableConfig? = null

    override var lowerBound: Double = 0.0
    override var upperBound: Double = Double.POSITIVE_INFINITY
    override var cost: Double = 0.0
    override var type: VariableType = VariableType.Continuous
}

class MachineConfig(
    val machine: AnyMachine<*>,
    val setupConfig: SetupConfig,
)

@FactoryConfigDsl
class MachineConfigScope(
    val machine: BaseMachine<*>,
    override val prototypes: FactorioPrototypes,
    val setupConfig: SetupConfigBuilder = SetupConfigBuilder(),
) : ISetupConfigBuilder by setupConfig, WithFactorioPrototypes {
    var qualities: Set<Quality> = setOf(prototypes.defaultQuality)
    val moduleConfigs = mutableSetOf<ModuleConfig>(ModuleConfig())

    fun noEmptyModules() {
        moduleConfigs -= ModuleConfig()
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

    internal fun toMachineConfigs() = buildList {
        val setupConfig = setupConfig.build()
        for (quality in qualities) {
            val machine = machine.withQuality(quality)
            for (moduleConfig in moduleConfigs.ifEmpty { listOf(ModuleConfig()) }) {
                val machineWithModules = machine.withModulesOrNull(moduleConfig)
                if (machineWithModules != null) {
                    add(MachineConfig(machineWithModules, setupConfig))
                }
            }
        }
    }
}

typealias MachineConfigFn = MachineConfigScope.() -> Unit

class RecipeConfig(
    val recipe: RecipeOrResource<*>,
    val setupConfig: SetupConfig,
)

@FactoryConfigDsl
class RecipeConfigScope(
    val process: RecipeOrResource<*>,
    override val prototypes: FactorioPrototypes,
    val setupConfig: SetupConfigBuilder = SetupConfigBuilder(),
) : ISetupConfigBuilder by setupConfig, WithFactorioPrototypes {
    var qualities: Set<Quality> = setOf(prototypes.defaultQuality)
    fun allQualities() {
        this@RecipeConfigScope.qualities = qualities.toSet()
    }

    internal fun toRecipeConfigs() = buildList {
        val setupConfig = setupConfig.build()
        for (quality in qualities) {
            val recipe = process.withQualityOrNull(quality) ?: continue
            add(RecipeConfig(recipe, setupConfig))
        }
    }
}
typealias RecipeConfigFn = RecipeConfigScope.() -> Unit

class ProcessConfigScope(
    val process: MachineSetup<*>,
    val setupConfig: SetupConfigBuilder = SetupConfigBuilder(),
) : ISetupConfigBuilder by setupConfig

@FactoryConfigDsl
abstract class ConfigScope<T, S>(override val prototypes: FactorioPrototypes) : WithFactorioPrototypes {
    var defaultConfig: (S.() -> Unit)? = null
    val configs: MutableMap<T, MutableList<S.() -> Unit>> = mutableMapOf()
    fun default(block: S.() -> Unit) {
        defaultConfig = block
    }

    protected abstract fun createScope(item: T): S
    open fun addConfig(item: T, block: (S.() -> Unit)? = null) {
        val list = configs.getOrPut(item, ::ArrayList)
        if (block != null) list += block
    }

    operator fun T.invoke(block: (S.() -> Unit)? = null) {
        addConfig(this, block)
    }

    internal fun allScopes() = buildList {
        for ((item, configs) in configs) {
            val scope = createScope(item)
            defaultConfig?.invoke(scope)
            for (config in configs) {
                config.invoke(scope)
            }
            add(scope)
        }
    }

    internal fun scopeFor(item: T): S? {
        if (defaultConfig == null && configs[item].isNullOrEmpty()) return null
        val scope = createScope(item)
        defaultConfig?.invoke(scope)
        configs[item]?.forEach { it.invoke(scope) }
        return scope
    }
}

@FactoryConfigDsl
class FactoryConfigBuilder(override val prototypes: FactorioPrototypes) : WithFactorioPrototypes {
    val machines = MachinesScope()
    inline fun machines(block: MachinesScope.() -> Unit) = machines.block()

    @FactoryConfigDsl
    inner class MachinesScope : ConfigScope<BaseMachine<*>, MachineConfigScope>(prototypes) {
        override fun createScope(item: BaseMachine<*>) = MachineConfigScope(item, prototypes)

        operator fun String.invoke(block: MachineConfigFn? = null) {
            this@FactoryConfigBuilder.machine(this)(block)
        }
    }

    val recipes = RecipesScope()
    inline fun recipes(block: RecipesScope.() -> Unit) = recipes.block()

    @FactoryConfigDsl
    inner class RecipesScope : ConfigScope<RecipeOrResource<*>, RecipeConfigScope>(prototypes) {
        init {
            defaultConfig = {
                cost = DefaultWeights.RECIPE_COST
            }
        }

        override fun createScope(item: RecipeOrResource<*>): RecipeConfigScope = RecipeConfigScope(item, prototypes)

        operator fun Item.invoke(block: RecipeConfigFn? = null) {
            prototypes.recipeOf(this)(block)
        }

        operator fun String.invoke(block: RecipeConfigFn? = null) {
            recipe(this)(block)
        }

        fun allCraftingRecipes(config: RecipeConfigFn? = null) {
            for (recipe in prototypes.recipes.values) {
                recipe(config)
            }
        }

        fun allOfCategory(category: String, config: RecipeConfigFn?) {
            prototypes.recipesByCategory[RecipeCategoryID(category)]?.forEach { recipe ->
                recipe(config)
            }
        }

        fun remove(recipe: RecipeOrResource<*>) {
            configs.remove(recipe)
        }

        fun remove(item: Item) {
            remove(prototypes.recipeOf(item))
        }

        fun remove(entity: Entity) {
            remove(entity.item())
        }
    }

    val setups = SetupScope()
    inline fun setups(block: SetupScope.() -> Unit) = setups.block()

    @FactoryConfigDsl
    inner class SetupScope : ConfigScope<MachineSetup<*>, ProcessConfigScope>(prototypes) {
        override fun createScope(item: MachineSetup<*>) = ProcessConfigScope(item)

        override fun addConfig(item: MachineSetup<*>, block: (ProcessConfigScope.() -> Unit)?) {
            this@FactoryConfigBuilder.machines.addConfig(item.machine.baseMachine())
            this@FactoryConfigBuilder.recipes.addConfig(item.recipe)
            super.addConfig(item, block)
        }
    }

    var researchConfig: ResearchConfig = ResearchConfig()

    private fun getAllProcesses(): List<RealProcess> = buildList {
        val machineConfigs = machines.allScopes().flatMap { it.toMachineConfigs() }
        val recipeConfigs = recipes.allScopes().flatMap { it.toRecipeConfigs() }
        val (crafting, mining) = recipeConfigs.partition { it.recipe is Recipe }
        val craftingById = crafting.groupBy { (it.recipe as Recipe).prototype.category }
        val miningById = mining.groupBy { (it.recipe as Resource).prototype.category }
        for (machineConfig in machineConfigs) {
            val machine = machineConfig.machine
            val buildCost = machine.getBuildCost(prototypes)
            val prototype = machine.prototype
            val recipeConfigs = when (prototype) {
                is CraftingMachinePrototype -> prototype.crafting_categories.flatMap { craftingById[it].orEmpty() }
                is MiningDrillPrototype -> prototype.resource_categories.flatMap { miningById[it].orEmpty() }
                else -> error("Unknown machine type: $prototype")
            }
            for (recipeConfig in recipeConfigs) {
                val setup = machine.processingOrNullCast(recipeConfig.recipe) ?: continue

                val setupConfigScope = setups.scopeFor(setup)
                val config = (machineConfig.setupConfig + recipeConfig.setupConfig)
                    .let {
                        if (setupConfigScope != null) it + setupConfigScope.setupConfig.build()
                        else it
                    }
                val costs = config.additionalCosts + if (config.includeBuildCosts) buildCost else emptyVector()
                add(
                    RealProcess(
                        process = setup,
                        variableConfig = config.variableConfig(),
                        additionalCosts = costs,
                        costVariableConfig = config.costVariableConfig
                    )
                )
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
