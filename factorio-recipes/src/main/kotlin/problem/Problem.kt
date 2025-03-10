package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.Entity
import glassbricks.factorio.recipes.FactorioPrototypes
import glassbricks.factorio.recipes.FactorioPrototypesScope
import glassbricks.factorio.recipes.maybeWithQuality
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Rate
import glassbricks.recipeanalysis.Symbol
import glassbricks.recipeanalysis.lp.SymbolConstraint
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.VariableConfigBuilder
import glassbricks.recipeanalysis.lp.leq
import glassbricks.recipeanalysis.recipelp.*
import glassbricks.recipeanalysis.uvec
import kotlin.math.min

object DefaultWeights {
    const val INPUT_COST = 1e4
    const val INPUT_RATE_COST = 1.0

    const val MAXIMIZE_OUTPUT_COST = 1e8
    const val SURPLUS_COST: Double = 1e-4
}

@FactoryConfigDsl
class ProblemBuilder(
    val prototypes: FactorioPrototypes,
    factoryConfig: FactoryConfig? = null,
) {
    constructor(factoryConfig: FactoryConfig) : this(factoryConfig.prototypes, factoryConfig)

    var factory: FactoryConfig? = factoryConfig
        set(value) {
            if (value != null) require(value.prototypes == prototypes) { "Prototypes used in factory config do not match" }
            field = value
        }

    fun factory(factoryConfig: FactoryConfig) {
        this.factory = factoryConfig
    }

    inline fun factory(block: FactoryConfigBuilder.() -> Unit) {
        factory(prototypes.factory(block))
    }

    val inputs = mutableListOf<Input>()
    fun input(ingredient: Ingredient, cost: Double = DefaultWeights.INPUT_COST, limit: Rate = Rate.infinity) {
        inputs += Input(
            ingredient,
            variableConfig = VariableConfig(cost = cost, upperBound = limit.ratePerSecond)
        )
    }

    fun limit(ingredient: Ingredient, rate: Rate) {
        input(ingredient, cost = DefaultWeights.INPUT_RATE_COST, limit = rate)
    }

    val outputs = mutableListOf<Output>()
    fun output(
        ingredient: Ingredient,
        rate: Rate,
        weight: Double = 0.0,
    ) {
        outputs += Output(
            ingredient,
            variableConfig = VariableConfig(lowerBound = rate.ratePerSecond, cost = -weight)
        )
    }

    fun optionalOutput(
        ingredient: Ingredient,
        rate: Rate = Rate.zero,
        weight: Double = 0.0,
    ) {
        output(ingredient, rate = rate, weight = weight)
    }

    fun maximize(ingredient: Ingredient, weight: Double = DefaultWeights.MAXIMIZE_OUTPUT_COST, rate: Rate = Rate.zero) {
        output(ingredient, rate = rate, weight = weight)
    }

    val symbolConstraints: MutableList<SymbolConstraint> = mutableListOf()
    val symbolConfigs: MutableMap<Symbol, VariableConfigBuilder> = mutableMapOf()

    inline fun costs(block: CostsScope.() -> Unit) {
        CostsScope().apply(block)
    }

    @FactoryConfigDsl
    inner class CostsScope {
        val prototypes get() = this@ProblemBuilder.prototypes
        fun varConfig(symbol: Symbol): VariableConfigBuilder {
            return this@ProblemBuilder.symbolConfigs.getOrPut(symbol) { VariableConfigBuilder() }
        }

        fun limit(symbol: Symbol, value: Number) {
            val config = varConfig(symbol)
            config.upperBound = min(value.toDouble(), config.upperBound)
        }

        fun limit(entity: Entity, value: Number) {
            limit(this@ProblemBuilder.prototypes.itemOf(entity), value)
        }

        fun costOf(symbol: Symbol, value: Number) {
            varConfig(symbol).cost = value.toDouble()
        }

        fun costOf(entity: Entity, value: Number) {
            costOf(this@ProblemBuilder.prototypes.itemOf(entity), value)
        }

        fun forbidIfNotSpecified(symbol: Symbol) {
            if (symbol !in this@ProblemBuilder.symbolConfigs) {
                limit(symbol, 0.0)
            }
        }

        fun forbidUnspecifiedEntities() = with(this@ProblemBuilder.prototypes) {
            for (quality in prototypes.qualities) {
                for (machine in prototypes.craftingMachines.values) {
                    forbidIfNotSpecified(
                        machine.item().withQuality(quality)
                    )
                }
                for (drill in prototypes.miningDrills.values) {
                    forbidIfNotSpecified(drill.item().withQuality(quality))
                }
                for (beacon in prototypes.beacons.values) {
                    forbidIfNotSpecified(beacon.item().withQuality(quality))
                }
            }
        }

        fun forbidUnspecifiedModules() = with(this@ProblemBuilder.prototypes) {
            for (module in prototypes.modules.values) {
                for (quality in prototypes.qualities) {
                    forbidIfNotSpecified(module.withQuality(quality))
                }
            }
        }

        fun forbidAllUnspecified() {
            forbidUnspecifiedEntities()
            forbidUnspecifiedModules()
        }

        infix fun Ingredient.producedBy(production: ProductionOverTime) {
            // usage of symbol <= (stage.output of symbol) * time
            this@ProblemBuilder.symbolConstraints +=
                uvec(this) leq production.productionOf(this)
            // get config so forbidAllUnspecified() doesn't remove it
            varConfig(this)
        }
    }

    val customProcesses: MutableList<PseudoProcess> = mutableListOf()
    inline fun customProcess(name: String, block: CustomProcessBuilder.() -> Unit) {
        customProcesses += CustomProcessBuilder(name).apply(block).build()
    }

    var surplusCost: Double = DefaultWeights.SURPLUS_COST

    /**
     * If to apply an optimization to remove recipes/items that cannot be produced, given the input. Ignores quality.
     *
     * Note: the LP solver also does this, but doing it ourselves first is much more performant,
     *       since we work with recipes before they are multiplied by every quality/machine/module combination.
     */
    var removeUnusableRecipes: Boolean = true
    var verifyOutputsProducible: Boolean = true

    fun build(): ProductionLp = with(prototypes) {
        var factory = factory ?: error("Factory not set")

        if (removeUnusableRecipes) {
            val inputItems = inputs.map { it.ingredient.maybeWithQuality(prototypes.defaultQuality) }
            val (newFactory, producibleIngredients) = factory.removeUnusableRecipes(
                inputItems,
                customProcesses
            )
            factory = newFactory
            if (verifyOutputsProducible) {
                val nonProducibleOutputs = outputs
                    .map { it.ingredient.maybeWithQuality(prototypes.defaultQuality) }
                    .filterNot { it in producibleIngredients }
                require(nonProducibleOutputs.isEmpty()) {
                    "These outputs are not producible given inputs: $nonProducibleOutputs"
                }
            }
        }

        return ProductionLp(
            inputs = inputs,
            outputs = outputs,
            processes = factory.getAllProcesses(),
            otherProcesses = customProcesses,
            surplusCost = surplusCost,
            constraints = symbolConstraints,
            symbolConfigs = symbolConfigs.mapValues { it.value.build() },
        )
    }
}

inline fun FactorioPrototypesScope.problem(block: ProblemBuilder.() -> Unit): ProductionLp =
    ProblemBuilder(prototypes).apply(block).build()

inline fun FactoryConfig.problem(block: ProblemBuilder.() -> Unit): ProductionLp =
    ProblemBuilder(this).apply(block).build()

inline fun FactoryConfig.stage(
    name: String? = null,
    block: ProblemBuilder.() -> Unit,
): ProductionStage =
    problem(block).toStage(name)
