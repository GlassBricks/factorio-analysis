package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.FactorioPrototypes
import glassbricks.factorio.recipes.WithFactorioPrototypes
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.ConstraintDsl
import glassbricks.recipeanalysis.lp.SymbolConstraint
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.VariableConfigBuilder
import glassbricks.recipeanalysis.recipelp.*

object DefaultWeights {
    const val RECIPE_COST = 1.0
    const val INPUT_COST = 1e4
    const val INPUT_RATE_COST = 1.0

    const val MAXIMIZE_OUTPUT_COST = 1e8
    const val SURPLUS_COST: Double = 1e-4
}

@RecipesConfigDsl
class ProblemBuilder(
    override val prototypes: FactorioPrototypes,
    factoryConfig: FactoryConfig? = null,
) : WithFactorioPrototypes {
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
            variableConfig = VariableConfig(cost = cost, upperBound = limit.perSecond)
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
            variableConfig = VariableConfig(lowerBound = rate.perSecond, cost = -weight)
        )
    }

    fun maximize(ingredient: Ingredient, weight: Double = DefaultWeights.MAXIMIZE_OUTPUT_COST, rate: Rate = Rate.zero) {
        output(ingredient, rate = rate, weight = weight)
    }

    val symbolConstraints: MutableList<SymbolConstraint> = mutableListOf()
    val symbolConfigs: MutableMap<Symbol, VariableConfigBuilder> = mutableMapOf()

    inline fun costs(block: CostsScope.() -> Unit) {
        CostsScope().apply(block)
    }

    inner class CostsScope : ConstraintDsl<Symbol> {
        override val constraints get() = this@ProblemBuilder.symbolConstraints

        fun getConfig(symbol: Symbol): VariableConfigBuilder {
            return symbolConfigs.getOrPut(symbol) { VariableConfigBuilder() }
        }

        fun limit(symbol: Symbol, value: Number) {
            uvec(symbol) leq value
        }

        fun costOf(symbol: Symbol, value: Number) {
            getConfig(symbol).cost = value.toDouble()
        }
    }

    val customProcesses: MutableList<PseudoProcess> = mutableListOf()
    inline fun customProcess(name: String, block: CustomProcessBuilder.() -> Unit) {
        customProcesses += CustomProcessBuilder(name).apply(block).build()
    }

    var surplusCost: Double = DefaultWeights.SURPLUS_COST

    fun build(): ProductionLp = ProductionLp(
        processes = concat(
            inputs,
            outputs,
            (factory ?: error("Factory not set")).allProcesses,
            customProcesses,
        ),
        surplusCost = surplusCost,
        constraints = symbolConstraints,
        symbolConfigs = symbolConfigs.mapValues { it.value.build() },
    )
}

inline fun WithFactorioPrototypes.problem(block: ProblemBuilder.() -> Unit): ProductionLp =
    ProblemBuilder(prototypes).apply(block).build()

inline fun FactoryConfig.problem(block: ProblemBuilder.() -> Unit): ProductionLp =
    ProblemBuilder(this).apply(block).build()
