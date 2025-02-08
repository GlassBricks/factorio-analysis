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

class MultiStageProductionLp(
    val stages: List<ProductionStage>,
    val additionalConstraints: List<KeyedConstraint<ReferenceSymbol>> = emptyList(),
) {
    fun solve(solver: LpSolver = DefaultLpSolver(), options: LpOptions = LpOptions()): MultiStageRecipeResult {
        val lpVars = createVars(stages)
        val lp = createLp(lpVars)
        val result = solver.solve(lp, options)
        val solutions = result.solution?.let {
            lpVars.mapValues { (_, lpVars) -> lpVars.createSolution(it) }
        }
        return MultiStageRecipeResult(
            lpResult = result,
            solutions = solutions,
        )
    }

    private fun createVars(stages: List<ProductionStage>): Map<ProductionStage, ProductionLpVars> =
        buildMap {
            for (stage in stages) {
                if (stage in this) {
                    error("There cannot be duplicates in multi-stage recipe")
                }
                this[stage] = stage.productionLp.createVarsAndConstraints(this)
            }
        }

    private fun createLp(lpVars: Map<ProductionStage, ProductionLpVars>): LpProblem {
        val allConstraints = lpVars.values.flatMapTo(mutableListOf()) { it.constraints }
        for (constraint in additionalConstraints) {
            allConstraints.add(constraint.mapKeys {
                val stageVars = lpVars[it.stage] ?: error("Referenced stage ${it.stage} not found")
                it.resolveVariable(stageVars)
            })
        }
        val allObjectiveWeights = lpVars.values.map { it.objectiveWeights }.flattenMaps()
        return LpProblem(
            constraints = allConstraints,
            objective = Objective(allObjectiveWeights, maximize = false)
        )
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
class ProductionOverTime(val factories: MapVector<ProductionStage, Time>) {
    fun productionOf(ingredient: Ingredient): Vector<ReferenceSymbol> {
        val amounts = factories.entries.associateNotNull { (stage, time) ->
            val outputs = stage.productionLp.outputsByIngredient[ingredient] ?: return@associateNotNull null
            val output = outputs.singleOrNull() ?: error("Multiple outputs for $ingredient in $stage")
            stage.ref(output) to time
        }
        return vector(amounts)
    }

    operator fun plus(otherProduction: ProductionOverTime): ProductionOverTime =
        ProductionOverTime(factories + otherProduction.factories)
}

fun ProductionStage.runningFor(time: Duration): ProductionOverTime =
    ProductionOverTime(vectorWithUnits<Time, ProductionStage>(this to time.toDouble(DurationUnit.SECONDS)))
