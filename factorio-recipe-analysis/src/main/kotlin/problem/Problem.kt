package glassbricks.factorio.recipes.problem

import glassbricks.factorio.recipes.FactorioPrototypes
import glassbricks.factorio.recipes.MachineSetup
import glassbricks.factorio.recipes.WithFactorioPrototypes
import glassbricks.recipeanalysis.*

/** Wrapper around a [RecipeLp] */
class Problem(
    val factory: FactoryConfig,
    inputs: List<Input>,
    outputs: List<Output>,
    customProcesses: List<PseudoProcess>,
    val constraints: List<SymbolConstraint>,
    val symbolConfigs: Map<Symbol, VariableConfig>,
    surplusCost: Double,
    lpOptions: LpOptions,
) {

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
        lpOptions = lpOptions,
        constraints = constraints,
        symbolConfigs = symbolConfigs,
    )

    fun solve(): Result = Result(this, recipeLp.solve())
    fun solveIncremental(): IncrementalSolver<Result> = recipeLp.createIncrementalSolver().map { Result(this, it) }
}

class Result(
    val problem: Problem,
    val recipeResult: RecipeLpResult,
) {
    val solution: RecipeLpSolution? get() = recipeResult.solution
    val status: LpResultStatus get() = recipeResult.lpResult.status
    val lpResult: LpResult get() = recipeResult.lpResult
    val lpSolution: LpSolution? get() = lpResult.solution
    fun outputRate(ingredient: Ingredient): Rate? {
        val output = problem.outputs[ingredient] ?: return null
        val usage = solution?.recipeUsage ?: return null
        return Rate(output.sumOf { usage[it] })
    }

    fun inputRate(ingredient: Ingredient): Rate? {
        val input = problem.inputs[ingredient] ?: return null
        val usage = solution?.recipeUsage ?: return null
        return Rate(input.sumOf { usage[it] })
    }

    fun amountUsed(recipe: MachineSetup<*>): Double? {
        val usage = solution?.recipeUsage ?: return 0.0
        val lpProcess = problem.recipes[recipe] ?: return 0.0
        return usage[lpProcess]
    }
}

object DefaultWeights {
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
        inputs += Input(ingredient, cost = cost, upperBound = limit.perSecond)
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
        outputs += Output(ingredient = ingredient, weight = weight, lowerBound = rate.perSecond)
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
        lpOptions = lpOptions,
        symbolConfigs = symbolConfigs.mapValues { it.value.build() },
    )
}

inline fun WithFactorioPrototypes.problem(block: ProblemBuilder.() -> Unit): Problem =
    ProblemBuilder(prototypes).apply(block).build()

inline fun FactoryConfig.problem(block: ProblemBuilder.() -> Unit): Problem =
    ProblemBuilder(this).apply(block).build()
