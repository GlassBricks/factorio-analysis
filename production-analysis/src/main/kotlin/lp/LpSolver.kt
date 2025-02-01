package glassbricks.recipeanalysis.lp

import glassbricks.recipeanalysis.Vector
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class Objective(
    val coefficients: Map<Variable, Double>,
    val constant: Double = 0.0,
    val maximize: Boolean = true,
)

data class LpProblem(
    val constraints: List<Constraint>,
    val objective: Objective,
)

interface LpSolver {
    fun solve(problem: LpProblem, options: LpOptions = LpOptions()): LpResult
}

@Suppress("FunctionName")
fun DefaultLpSolver(): LpSolver = OrToolsLp()

class LpOptions(
    val timeLimit: Duration = 1.minutes,
    val checkSolution: Boolean = true,
    val epsilon: Double = 1e-5,
    val enableLogging: Boolean = false,
    val hintFromRoundingUpSemiContinuousVars: Boolean = false,
    val numThreads: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
)

interface LpResult {
    val status: LpResultStatus
    val bestBound: Double
    val solution: LpSolution?
}

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
