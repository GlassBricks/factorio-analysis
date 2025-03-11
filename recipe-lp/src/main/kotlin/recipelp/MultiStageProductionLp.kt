package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.*
import kotlin.time.Duration
import kotlin.time.DurationUnit

class ProductionStage(
    val productionLp: ProductionLp,
    val name: String? = null,
) {
    override fun toString(): String = name ?: productionLp.toString()
}

fun ProductionLp.toStage(name: String? = null): ProductionStage = ProductionStage(this, name)

/**
 * A symbol, that when converted to a variable in RecipeLp, will actually be a reference to a variable in another stage.
 *
 * This can be used to create constraints that link multiple stages together.
 *
 * This also allows multiple stages to use the same symbol independently; using a ReferenceSymbol can disambiguate
 * between them.
 */
interface ReferenceSymbol : Symbol {
    val stage: ProductionStage
    fun resolveVariable(stageVars: ProductionLpVars): Variable
}

/** References a process in another stage. */
data class ProcessReference(
    override val stage: ProductionStage,
    val process: PseudoProcess,
) : ReferenceSymbol {
    override fun resolveVariable(stageVars: ProductionLpVars): Variable =
        stageVars.processVariables[process] ?: error("No variable for $process in $stage")

    override fun toString(): String {
        return "($stage):$process"
    }
}

fun ProductionStage.ref(process: PseudoProcess): ProcessReference = ProcessReference(this, process)

fun maybeResolveReferenceVar(
    symbol: Symbol,
    vars: Map<ProductionStage, ProductionLpVars>,
): Variable? = when (symbol) {
    is ReferenceSymbol -> symbol.resolveVariablesIn(vars)
    else -> null
}

private fun ReferenceSymbol.resolveVariablesIn(vars: Map<ProductionStage, ProductionLpVars>): Variable =
    resolveVariable(vars[stage] ?: error("Stage $stage not found"))

class MultiStageProductionLp(
    val stages: List<ProductionStage>,
    val additionalConstraints: List<KeyedConstraint<ReferenceSymbol>> = emptyList(),
) {
    fun solve(solver: LpSolver = DefaultLpSolver(), options: LpOptions = LpOptions()): MultiStageRecipeResult {
        val lpVars = createVars(solver, stages)
        addAdditionalConstraints(solver, lpVars)
        val result = solver.solve(options)
        val solutions = result.solution?.let {
            lpVars.mapValues { (_, lpVars) -> lpVars.createSolution(it) }
        }
        return MultiStageRecipeResult(
            lpResult = result,
            solutions = solutions,
        )
    }

    private fun createVars(
        solver: LpSolver,
        stages: List<ProductionStage>,
    ): Map<ProductionStage, ProductionLpVars> = buildMap {
        for (stage in stages) {
            if (stage in this@buildMap) {
                error("There cannot be duplicates in multi-stage recipe")
            }
            this[stage] = stage.productionLp.createVarsAndConstraints(solver, this)
        }
    }

    private fun addAdditionalConstraints(
        solver: LpSolver,
        lpVars: Map<ProductionStage, ProductionLpVars>,
    ) {
        for (constraint in additionalConstraints) {
            solver += constraint.mapKeys { it.resolveVariablesIn(lpVars) }
        }
    }
}

data class MultiStageRecipeResult(
    val lpResult: LpResult,
    val solutions: Map<ProductionStage, RecipeSolution>?,
) {
    val lpSolution: LpSolution? get() = lpResult.solution
    val status: LpResultStatus get() = lpResult.status
}

/** A DSL construct */
class ProductionOverTime(val factories: AnyVector<ProductionStage, Time>) {
    fun productionOf(ingredient: Ingredient): Vector<ReferenceSymbol> = buildVector {
        for ((stage, time) in factories) {
            val outputs = stage.productionLp.outputsByIngredient[ingredient] ?: continue
            val output = outputs.singleOrNull() ?: error("Multiple outputs for $ingredient in $stage")
            inc(stage.ref(output), time)
        }
    }

    operator fun plus(otherProduction: ProductionOverTime): ProductionOverTime =
        ProductionOverTime(factories + otherProduction.factories)
}

fun ProductionStage.runningFor(time: Duration): ProductionOverTime =
    ProductionOverTime(vectorOfWithUnits<Time, ProductionStage>(this to time.toDouble(DurationUnit.SECONDS)))
