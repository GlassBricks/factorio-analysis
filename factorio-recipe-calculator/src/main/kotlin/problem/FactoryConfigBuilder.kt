package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.Vector
import glassbricks.recipeanalysis.emptyVector
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.VariableType

@DslMarker
annotation class FactoryConfigDsl

interface Builder<T> {
    fun build(): T
}

interface ISetupConfigBuilder {
    var includeBuildCosts: Boolean
    var includePowerCosts: Boolean
    var additionalCosts: Vector<Symbol>
    var outputVariableConfig: VariableConfig?

    var lowerBound: Double
    var upperBound: Double
    var cost: Double
    var type: VariableType

    fun includeBuildCosts() {
        includeBuildCosts = true
    }

    fun includePowerCosts() {
        includePowerCosts = true
    }
}

class SetupConfigBuilder : ISetupConfigBuilder, Builder<SetupConfig> {
    override var includeBuildCosts: Boolean = false
    override var includePowerCosts: Boolean = false
    override var additionalCosts: Vector<Symbol> = emptyVector()
    override var outputVariableConfig: VariableConfig? = null

    override var lowerBound: Double = 0.0
    override var upperBound: Double = Double.POSITIVE_INFINITY
    override var cost: Double = 0.0
    override var type: VariableType = VariableType.Continuous
    override fun build(): SetupConfig = SetupConfig(
        includeBuildCosts = includeBuildCosts,
        additionalCosts = additionalCosts,
        costVariableConfig = outputVariableConfig,
        lowerBound = lowerBound,
        upperBound = upperBound,
        cost = cost,
        variableType = type,
        includePowerCosts = includePowerCosts
    )
}

@FactoryConfigDsl
class MachineConfigBuilder(
    val machine: BaseMachine<*>,
    prototypes: FactorioPrototypes,
    val setupConfig: SetupConfigBuilder = SetupConfigBuilder(),
) : ISetupConfigBuilder by setupConfig, Builder<MachineConfig> {
    var qualities = setOf(prototypes.defaultQuality)
    val moduleSetConfigs = mutableSetOf<ModuleSetConfig>(ModuleSetConfig())

    fun noEmptyModules() {
        moduleSetConfigs -= ModuleSetConfig()
    }

    fun moduleConfig(
        vararg modules: WithModuleCount,
        fill: Module? = null,
        beacons: List<WithBeaconCount> = emptyList(),
    ) {
        moduleSetConfigs += ModuleSetConfig(
            modules = modules.asList(),
            fill = fill,
            beacons = beacons,
        )
    }

    override fun build(): MachineConfig = MachineConfig(
        setupConfig = setupConfig.build(),
        qualities = qualities,
        moduleSets = moduleSetConfigs.mapNotNull {
            val moduleSlots = machine.prototype.module_slots?.toInt() ?: return@mapNotNull null
            it.toModuleSet(moduleSlots)
        }
    )
}

@FactoryConfigDsl
class RecipeConfigBuilder(
    val recipe: RecipeOrResource<*>,
    override val prototypes: FactorioPrototypes,
    val setupConfig: SetupConfigBuilder = SetupConfigBuilder(),
) : ISetupConfigBuilder by setupConfig, Builder<RecipeConfig>, FactorioPrototypesScope by prototypes {
    var qualities: Set<Quality> = setOf(prototypes.defaultQuality)
    fun allQualities() {
        this@RecipeConfigBuilder.qualities = prototypes.qualities.toSet()
    }

    override fun build(): RecipeConfig = RecipeConfig(
        setupConfig = setupConfig.build(),
        qualities = qualities
    )
}

@FactoryConfigDsl
abstract class ConfigScope<T, B> {
    private var defaultConfig: (B.() -> Unit)? = null
    private var defaultConfigUsed = false
    val configs: MutableMap<T, B> = mutableMapOf()

    fun default(block: B.() -> Unit) {
        require(defaultConfigUsed == false) {
            "Default config already used. Trying to set the default in multiple places can make people very confused."
        }
        defaultConfig = block
        defaultConfigUsed = true
    }

    open fun addConfig(item: T, block: (B.() -> Unit)? = null) {
        getOrCreateBuilder(item)
            .also { block?.invoke(it) }
    }

    operator fun T.invoke(block: (B.() -> Unit)? = null) = addConfig(this, block)

    protected abstract fun createBuilder(item: T): B

    protected fun getOrCreateBuilder(item: T): B = configs.getOrPut(item) {
        createBuilder(item)
            .also { defaultConfig?.invoke(it) }
    }
}

private fun <T, R, B : Builder<R>> ConfigScope<T, B>.build(): Map<T, R> {
    return configs.mapValues { it.value.build() }
}

@FactoryConfigDsl
class FactoryConfigBuilder(val prototypes: FactorioPrototypes) : Builder<FactoryConfig> {
    var researchConfig: ResearchConfig = ResearchConfig()

    val machines = MachinesScope()
    inline fun machines(block: MachinesScope.() -> Unit) = machines.block()

    @FactoryConfigDsl
    inner class MachinesScope : ConfigScope<BaseMachine<*>, MachineConfigBuilder>() {
        override fun createBuilder(item: BaseMachine<*>): MachineConfigBuilder =
            MachineConfigBuilder(item, this@FactoryConfigBuilder.prototypes)

        operator fun String.invoke(block: MachineConfigBuilder.() -> Unit) {
            addConfig(this@FactoryConfigBuilder.prototypes.machine(this), block)
        }
    }

    val recipes = RecipesScope()
    inline fun recipes(block: RecipesScope.() -> Unit) = recipes.block()

    @FactoryConfigDsl
    inner class RecipesScope : ConfigScope<RecipeOrResource<*>, RecipeConfigBuilder>() {
        override fun createBuilder(item: RecipeOrResource<*>): RecipeConfigBuilder =
            RecipeConfigBuilder(item, this@FactoryConfigBuilder.prototypes)

        operator fun Item.invoke(block: RecipeConfigBuilder.() -> Unit = {}) {
            addConfig(
                this@FactoryConfigBuilder.prototypes.recipeOf(this),
                block
            )
        }

        operator fun String.invoke(block: RecipeConfigBuilder.() -> Unit = {}) {
            addConfig(
                this@FactoryConfigBuilder.prototypes.recipe(this),
                block
            )
        }

        fun allCraftingRecipes(config: RecipeConfigBuilder.() -> Unit = {}) {
            for (recipe in this@FactoryConfigBuilder.prototypes.recipes.values) {
                addConfig(recipe, config)
            }
        }

        fun remove(recipe: RecipeOrResource<*>) {
            configs.remove(recipe)
        }

        fun remove(item: Item) {
            remove(recipe = this@FactoryConfigBuilder.prototypes.recipeOf(item))
        }

        fun remove(entity: Entity) {
            remove(item = this@FactoryConfigBuilder.prototypes.itemOf(entity))
        }
    }

    val setups = SetupsScope()
    inline fun setups(block: SetupsScope.() -> Unit) = setups.block()

    @FactoryConfigDsl
    inner class SetupsScope : ConfigScope<MachineSetup<*>, SetupConfigBuilder>() {
        var addSetupMachines = true
        override fun createBuilder(item: MachineSetup<*>): SetupConfigBuilder = SetupConfigBuilder()
        override fun addConfig(item: MachineSetup<*>, block: (SetupConfigBuilder.() -> Unit)?) {
            val (machine, recipe) = item
            if (addSetupMachines) {
                this@FactoryConfigBuilder.machines.addConfig(machine.baseMachine()) {
                    qualities += machine.quality
                    moduleSetConfigs += machine.moduleSet.toModuleSetConfig()
                }
                this@FactoryConfigBuilder.recipes.addConfig(recipe)
            }

            val defaultQuality = this@FactoryConfigBuilder.prototypes.defaultQuality
            require(machine.quality == defaultQuality && recipe.inputQuality == defaultQuality) {
                "Setups can only be specified for default quality machines and recipes"
            }
            super.addConfig(item, block)
        }
    }

    override fun build(): FactoryConfig = FactoryConfig(
        prototypes = prototypes,
        research = researchConfig,
        machines = machines.build(),
        recipes = recipes.build(),
        setups = setups.build()
    )
}

private fun ModuleSet?.toModuleSetConfig(): ModuleSetConfig {
    if (this == null) return ModuleSetConfig()
    return ModuleSetConfig(
        modules = this.modules.moduleCounts,
        beacons = this.beacons.beaconCounts
    )
}

inline fun FactorioPrototypesScope.factory(block: FactoryConfigBuilder.() -> Unit): FactoryConfig {
    val builder = FactoryConfigBuilder(prototypes)
    builder.block()
    return builder.build()
}
