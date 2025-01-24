package glassbricks.recipeanalysis

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class Variable(
    val name: String,
    val lowerBound: Double = Double.NEGATIVE_INFINITY,
    val upperBound: Double = Double.POSITIVE_INFINITY,
    val integral: Boolean = false,
) {
    override fun toString(): String = "Variable($name)"
}

enum class ComparisonOp {
    Leq, Geq, Eq;

    fun opString() = when (this) {
        Leq -> "<="
        Geq -> ">="
        Eq -> "=="
    }
}

data class Constraint(val lhs: Map<Variable, Double>, val op: ComparisonOp, val rhs: Double) {
    override fun toString(): String = lhs.entries.joinToString(
        prefix = "Constraint(",
        postfix = " ${op.opString()} $rhs)",
        separator = " + "
    ) { (variable, coefficient) -> "$variable * $coefficient" }
}

infix fun Map<Variable, Double>.leq(rhs: Double) = Constraint(this, ComparisonOp.Leq, rhs)
infix fun Map<Variable, Double>.geq(rhs: Double) = Constraint(this, ComparisonOp.Geq, rhs)
infix fun Map<Variable, Double>.eq(rhs: Double) = Constraint(this, ComparisonOp.Eq, rhs)

data class Objective(
    val coefficients: Map<Variable, Double>,
    val constant: Double = 0.0,
    val maximize: Boolean = true,
)

data class LpProblem(
    val constraints: List<Constraint>,
    val objective: Objective,
)

enum class LpResultStatus {
    Optimal,
    Infeasible,
    Unbounded,
    Feasible,
    Error
}

class LpSolution(
    val assignment: Vector<Variable>,
    val objective: Double,
)

interface LpResult {
    val status: LpResultStatus
    val solution: LpSolution?
}

class LpOptions(
    val timeLimit: Duration = 1.minutes,
    val solver: LpSolver = DefaultLpSolver(),
    val epsilon: Double = 1e-5,
)

fun LpProblem.solve(options: LpOptions): LpResult = options.solver.solveLp(this, options)

interface LpSolver {
    fun solveLp(problem: LpProblem, options: LpOptions = LpOptions()): LpResult
}

fun DefaultLpSolver(): LpSolver = OrToolsLp()

class OrToolsLp(val solverId: String? = null) : LpSolver {
    class Result(
        solver: MPSolver,
        override val status: LpResultStatus,
        variables: Map<Variable, MPVariable>,
        epsilon: Double,
    ) : LpResult {
        override val solution: LpSolution?

        init {
            val hasSolution = status == LpResultStatus.Optimal || status == LpResultStatus.Feasible
            solution = if (!hasSolution) null else {
                val assignment = variables.mapValuesNotNull {
                    it.value.solutionValue()
                        .let { if (it < epsilon) null else it }
                }
                val objective = solver.objective().value()
                LpSolution(vectorWithUnits(assignment), objective)
            }
        }
    }

    override fun solveLp(problem: LpProblem, options: LpOptions): Result {
        val (constraints, objective) = problem
        val variables = buildSet {
            for (constraint in constraints) {
                addAll(constraint.lhs.keys)
            }
            addAll(objective.coefficients.keys)
        }
        Loader.loadNativeLibraries()
        val solverId = solverId ?: when {
            variables.all { it.integral } -> "SAT"
            variables.any { it.integral } -> "SCIP"
            else -> "GLOP"
        }
        val solver = MPSolver.createSolver(solverId) ?: error("Solver not found")
        solver.setTimeLimit(options.timeLimit.inWholeMilliseconds)

        val mpVariables = variables.associateWith { solver.makeVar(it.lowerBound, it.upperBound, it.integral, it.name) }
        for (constraint in constraints) {
            val ct = solver.makeConstraint(0.0, 0.0)
            for ((variable, coefficient) in constraint.lhs) {
                val mpVariable = mpVariables[variable]!!
                ct.setCoefficient(mpVariable, coefficient)
            }
            when (constraint.op) {
                ComparisonOp.Leq -> ct.setBounds(Double.NEGATIVE_INFINITY, constraint.rhs)
                ComparisonOp.Geq -> ct.setBounds(constraint.rhs, Double.POSITIVE_INFINITY)
                ComparisonOp.Eq -> ct.setBounds(constraint.rhs, constraint.rhs)
            }
        }

        val mpObjective = solver.objective()!!
        mpObjective.setOptimizationDirection(objective.maximize)
        for ((variable, coefficient) in objective.coefficients) {
            val mpVariable = mpVariables[variable]!!
            mpObjective.setCoefficient(mpVariable, coefficient)
        }
        mpObjective.setOffset(objective.constant)

        println("Solving LP with $solverId")
        println("  Variables: ${mpVariables.size}")
        println("  Constraints: ${constraints.size}")

        val resultStatus = solver.solve()
        val status = when (resultStatus) {
            MPSolver.ResultStatus.OPTIMAL -> LpResultStatus.Optimal
            MPSolver.ResultStatus.FEASIBLE -> LpResultStatus.Feasible
            MPSolver.ResultStatus.INFEASIBLE -> LpResultStatus.Infeasible
            MPSolver.ResultStatus.UNBOUNDED -> LpResultStatus.Unbounded
            null, MPSolver.ResultStatus.ABNORMAL,
            MPSolver.ResultStatus.MODEL_INVALID,
            MPSolver.ResultStatus.NOT_SOLVED,
                -> LpResultStatus.Error
        }
        return Result(solver, status, mpVariables, options.epsilon)
    }
}
