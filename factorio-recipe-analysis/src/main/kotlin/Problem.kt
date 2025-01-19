package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.*

// wrapper types for now in case we want additional functionality later
class Problem(
    inputs: List<Input>,
    outputs: List<Output>,
    val factory: FactoryConfig,
    surplusCost: Double,
    lpOptions: LpOptions,
) {

    val inputs: Map<Ingredient, List<Input>> = inputs
        .groupBy { it.ingredient }
    val outputs: Map<Ingredient, List<Output>> = outputs
        .groupBy { it.ingredient }

    val recipeLp = RecipeLp(
        processes = concat(
            inputs,
            outputs,
            factory.allProcesses,
        ),
        surplusCost = surplusCost,
        lpOptions = lpOptions,
    )

    fun solve(): Solution = Solution(this, recipeLp.solve())
}

class Solution(
    val problem: Problem,
    val recipeResult: RecipeLpResult,
) {
    val lpSolution: RecipeLpSolution? get() = recipeResult.solution
    val status: LpResultStatus get() = recipeResult.lpResult.status
    fun outputRate(ingredient: Ingredient): Rate? {
        val output = problem.outputs[ingredient] ?: return null
        val usage = lpSolution?.recipes ?: return null
        return Rate(output.sumOf { usage[it] })
    }

    fun inputRate(ingredient: Ingredient): Rate? {
        val input = problem.inputs[ingredient] ?: return null
        val usage = lpSolution?.recipes ?: return null
        return Rate(input.sumOf { usage[it] })
    }
}

object DefaultWeights {
    val inputCost = 1e4
    val inputRateCost = 1.0

    val maximizeWeight = 1e8
    val outputCost = 1.0
    val defaultSurplusCost: Double = 1e-3
}

@RecipesConfigDsl
class ProblemBuilder(
    override val prototypes: FactorioPrototypes,
    factoryConfig: FactoryConfig? = null,
) : WithPrototypes {
    constructor(factoryConfig: FactoryConfig) : this(factoryConfig.prototypes, factoryConfig)

    var factory: FactoryConfig? = factoryConfig
        set(value) {
            if (value != null) require(value.prototypes == prototypes) { "Prototypes used in factory config do not match" }
            field = value
        }

    fun factory(factoryConfig: FactoryConfig) {
        this.factory = factoryConfig
    }

    inline fun factory(block: FactorioConfigBuilder.() -> Unit) {
        factory(prototypes.factory(block))
    }

    val inputs = mutableListOf<Input>()
    fun input(ingredient: Ingredient, cost: Double = DefaultWeights.inputCost, limit: Rate = Rate.infinity) {
        inputs += Input(ingredient, cost = cost, upperBound = limit.perSecond)
    }

    fun limit(ingredient: Ingredient, rate: Rate) {
        input(ingredient, cost = DefaultWeights.inputRateCost, limit = rate)
    }

    fun input(ingredient: String, cost: Double = DefaultWeights.inputCost, limit: Rate = Rate.infinity) {
        input(prototypes.ingredients.getValue(ingredient), cost, limit)
    }

    fun limit(ingredient: String, rate: Rate) {
        limit(prototypes.ingredients.getValue(ingredient), rate)
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

    fun output(ingredient: String, rate: Rate, weight: Double = DefaultWeights.outputCost) {
        output(prototypes.ingredients.getValue(ingredient), rate, weight)
    }

    fun maximize(ingredient: String, weight: Double = DefaultWeights.maximizeWeight) {
        maximize(prototypes.ingredients.getValue(ingredient), weight)
    }

    var surplusCost: Double = DefaultWeights.defaultSurplusCost
    var lpOptions: LpOptions = LpOptions()

    fun build(): Problem = Problem(
        inputs = inputs,
        outputs = outputs,
        factory = factory ?: error("Factory not set"),
        surplusCost = surplusCost,
        lpOptions = lpOptions,
    )
}

inline fun FactorioPrototypes.problem(block: ProblemBuilder.() -> Unit): Problem =
    ProblemBuilder(this).apply(block).build()
