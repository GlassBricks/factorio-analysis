package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.FactorioPrototypes
import glassbricks.factorio.recipes.FactorioRecipesFormatter
import glassbricks.factorio.recipes.MachineSetup
import glassbricks.factorio.recipes.WithFactorioPrototypes
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.*
import glassbricks.recipeanalysis.recipelp.*

/** Wrapper around a [glassbricks.recipeanalysis.recipelp.RecipeLp] */
class Problem(
    val factory: FactoryConfig,
    inputs: List<Input>,
    outputs: List<Output>,
    customProcesses: List<PseudoProcess>,
    val constraints: List<SymbolConstraint>,
    val symbolConfigs: Map<Symbol, VariableConfig>,
    surplusCost: Double,
    lpSolver: LpSolver,
    lpOptions: LpOptions,
) {
    val prototypes get() = factory.prototypes

    val inputs: Map<Ingredient, List<Input>> = inputs
        .groupBy { it.ingredient }
    val outputs: Map<Ingredient, List<Output>> = outputs
        .groupBy { it.ingredient }
    val recipes: Map<MachineSetup<*>, LpProcess> =
        factory.allProcesses.associateBy { it.process as MachineSetup<*> }

    val recipeLp = RecipeLp(
        processes = concat(
            inputs,
            outputs,
            factory.allProcesses,
            customProcesses,
        ),
        surplusCost = surplusCost,
        lpSolver = lpSolver,
        lpOptions = lpOptions,
        constraints = constraints,
        symbolConfigs = symbolConfigs,
    )

    fun solve(): Result = Result(this, recipeLp.solve())
}

class Solution(
    val problem: Problem,
    val recipeSolution: RecipeLpSolution,
) : Usages by recipeSolution {
    val prototypes get() = problem.prototypes
    fun display(formatter: RecipeLpFormatter = FactorioRecipesFormatter) = recipeSolution.textDisplay(formatter)
    fun outputRate(ingredient: Ingredient): Rate {
        val output = problem.outputs[ingredient] ?: return Rate.zero
        return Rate(output.sumOf { recipeSolution.processUsage[it] })
    }

    fun inputRate(ingredient: Ingredient): Rate {
        val input = problem.inputs[ingredient] ?: return Rate.zero
        return Rate(input.sumOf { recipeSolution.processUsage[it] })
    }

    fun amountUsed(recipe: MachineSetup<*>): Double {
        val lpProcess = problem.recipes[recipe] ?: return 0.0
        return recipeSolution.processUsage[lpProcess]
    }
}

class Result(
    val problem: Problem,
    val recipeResult: RecipeLpResult,
) {
    val solution: Solution? get() = recipeResult.solution?.let { Solution(problem, it) }

    val lpResult: LpResult get() = recipeResult.lpResult
    val lpSolution: LpSolution? get() = lpResult.solution
    val status: LpResultStatus get() = lpResult.status
}

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
            basisVec(symbol) leq value
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
    var lpSolver: LpSolver = DefaultLpSolver()
    var lpOptions: LpOptions = LpOptions()

    fun build(): Problem = Problem(
        inputs = inputs,
        outputs = outputs,
        factory = factory ?: error("Factory not set"),
        customProcesses = customProcesses,
        surplusCost = surplusCost,
        constraints = symbolConstraints,
        lpSolver = lpSolver,
        lpOptions = lpOptions,
        symbolConfigs = symbolConfigs.mapValues { it.value.build() },
    )
}

inline fun WithFactorioPrototypes.problem(block: ProblemBuilder.() -> Unit): Problem =
    ProblemBuilder(prototypes).apply(block).build()

inline fun FactoryConfig.problem(block: ProblemBuilder.() -> Unit): Problem =
    ProblemBuilder(this).apply(block).build()
