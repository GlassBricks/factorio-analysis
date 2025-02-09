package glassbricks.recipeanalysis.lp

import glassbricks.recipeanalysis.Vector
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface LpSolver {
    val variables: List<Variable>
    fun addVariable(
        lowerBound: Double,
        upperBound: Double,
        name: String = "",
        type: VariableType = VariableType.Continuous,
        cost: Double = 0.0,
    ): Variable

    val constraints: List<Constraint>
    fun addConstraint(lb: Double, ub: Double, name: String = ""): Constraint

    val objective: Objective

    val supportsIntegerPrograms: Boolean

    fun solve(options: LpOptions = LpOptions()): LpResult
}

@Suppress("FunctionName")
fun DefaultLpSolver(): LpSolver = OrToolsLp("GLOP")

class LpOptions(
    val timeLimit: Duration = 1.minutes,
    val checkSolution: Boolean = true,
    val epsilon: Double = 1e-5,
    val enableLogging: Boolean = false,
    val numThreads: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
)

data class LpResult(val status: LpResultStatus, val solution: LpSolution?, val bestBound: Double)

enum class LpResultStatus {
    Optimal,
    Infeasible,
    Unbounded,
    Feasible,
    Error
}

class LpSolution(
    val assignment: Vector<Variable>,
    val objectiveValue: Double,
)
