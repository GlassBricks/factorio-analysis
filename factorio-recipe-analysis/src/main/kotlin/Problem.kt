package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.*

/** Wrapper around a [RecipeLp] */
class Problem(
    val factory: FactoryConfig,
    inputs: List<Input>,
    outputs: List<Output>,
    val additionalConstraints: List<SymbolConstraint>,
    val symbolCosts: Map<Symbol, Double>,
    surplusCost: Double,
    lpOptions: LpOptions,
) {

    val inputs: Map<Ingredient, List<Input>> = inputs
        .groupBy { it.ingredient }
    val outputs: Map<Ingredient, List<Output>> = outputs
        .groupBy { it.ingredient }
    val recipes: Map<MachineProcess<*>, LpProcess> =
        factory.allProcesses.associateBy { it.process as MachineProcess<*> }

    val recipeLp = RecipeLp(
        processes = concat(
            inputs,
            outputs,
            factory.allProcesses,
        ),
        surplusCost = surplusCost,
        lpOptions = lpOptions,
        additionalConstraints = additionalConstraints,
        symbolCosts = symbolCosts,
    )

    fun solve(): Solution = Solution(this, recipeLp.solve())
}

class Solution(
    val problem: Problem,
    val recipeResult: RecipeLpResult,
) {
    val recipeSolution: RecipeLpSolution? get() = recipeResult.solution
    val status: LpResultStatus get() = recipeResult.lpResult.status
    val lpResult: LpResult get() = recipeResult.lpResult
    val lpSolution: LpSolution? get() = lpResult.solution
    fun outputRate(ingredient: Ingredient): Rate? {
        val output = problem.outputs[ingredient] ?: return null
        val usage = recipeSolution?.recipes ?: return null
        return Rate(output.sumOf { usage[it] })
    }

    fun inputRate(ingredient: Ingredient): Rate? {
        val input = problem.inputs[ingredient] ?: return null
        val usage = recipeSolution?.recipes ?: return null
        return Rate(input.sumOf { usage[it] })
    }

    fun amountUsed(recipe: MachineProcess<*>): Double? {
        val usage = recipeSolution?.recipes ?: return 0.0
        val lpProcess = problem.recipes[recipe] ?: return 0.0
        return usage[lpProcess]
    }
}

object DefaultWeights {
    const val inputCost = 1e4
    const val inputRateCost = 1.0

    const val maximizeWeight = 1e8
    const val outputCost = 1.0
    const val defaultSurplusCost: Double = 1e-4
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
    fun input(ingredient: Ingredient, cost: Double = DefaultWeights.inputCost, limit: Rate = Rate.infinity) {
        inputs += Input(ingredient, cost = cost, upperBound = limit.perSecond)
    }

    fun limit(ingredient: Ingredient, rate: Rate) {
        input(ingredient, cost = DefaultWeights.inputRateCost, limit = rate)
    }

    val outputs = mutableListOf<Output>()
    fun output(
        ingredient: Ingredient,
        rate: Rate,
        weight: Double = DefaultWeights.outputCost,
    ) {
        outputs += Output(ingredient = ingredient, weight = weight, lowerBound = rate.perSecond)
    }

    fun maximize(ingredient: Ingredient, weight: Double = DefaultWeights.maximizeWeight) {
        output(ingredient, rate = Rate.zero, weight = weight)
    }

    val symbolConstraints: MutableList<SymbolConstraint> = mutableListOf()
    val symbolCosts: MutableMap<Symbol, Double> = mutableMapOf()

    inline fun costs(block: CostsScope.() -> Unit) {
        CostsScope().apply(block)
    }

    inner class CostsScope : ConstraintDsl {
        override val constraints: MutableList<SymbolConstraint> get() = this@ProblemBuilder.symbolConstraints
        fun limit(symbol: Symbol, value: Number) {
            basisVec(symbol) leq value
        }

        fun limit(itemName: String, value: Number) {
            limit(this@ProblemBuilder.prototypes.item(itemName), value)
        }

        fun costOf(symbol: Symbol, value: Number) {
            symbolCosts[symbol] = value.toDouble()
        }
    }

    var surplusCost: Double = DefaultWeights.defaultSurplusCost
    var lpOptions: LpOptions = LpOptions()

    fun build(): Problem = Problem(
        inputs = inputs,
        outputs = outputs,
        factory = factory ?: error("Factory not set"),
        surplusCost = surplusCost,
        additionalConstraints = symbolConstraints,
        lpOptions = lpOptions,
        symbolCosts = symbolCosts
    )
}

inline fun WithFactorioPrototypes.problem(block: ProblemBuilder.() -> Unit): Problem =
    ProblemBuilder(prototypes).apply(block).build()

inline fun FactoryConfig.problem(block: ProblemBuilder.() -> Unit): Problem =
    ProblemBuilder(this).apply(block).build()
