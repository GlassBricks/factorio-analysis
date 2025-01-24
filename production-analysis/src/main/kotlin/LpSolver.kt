package glassbricks.recipeanalysis

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
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

@Suppress("FunctionName")
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
                        .let { if (it in -epsilon..epsilon) 0.0 else it }
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
            variables.all { it.type == VariableType.Integer } -> "SAT"
            variables.any { it.type != VariableType.Continuous } -> "SCIP"
            else -> "GLOP"
        }
        val solver = MPSolver.createSolver(solverId) ?: error("Solver not found")
        solver.setTimeLimit(options.timeLimit.inWholeMilliseconds)

        val mpVariables = variables.associateWith<_, MPVariable> { variable ->
            when (variable.type) {
                VariableType.Continuous, VariableType.Integer -> solver.makeVar(
                    variable.lowerBound,
                    variable.upperBound,
                    variable.type == VariableType.Integer,
                    variable.name
                )

                VariableType.SemiContinuous -> if (0.0 in variable.lowerBound..variable.upperBound) {
                    // simple continuous variable
                    solver.makeVar(
                        variable.lowerBound,
                        variable.upperBound,
                        false,
                        variable.name
                    )
                } else {
                    // create auxiliary variable
                    // min*aux <= variable <= max*aux
                    val mpVariable = solver.makeVar(
                        variable.lowerBound.coerceAtMost(0.0),
                        variable.upperBound.coerceAtLeast(0.0),
                        false,
                        variable.name
                    )
                    val minCoeff: Double
                    val maxCoeff: Double
                    val auxUb: Double
                    if (variable.upperBound == Double.POSITIVE_INFINITY) {
                        // min*aux <= variable <= (min*3)*aux; aux any integer
                        minCoeff = variable.lowerBound
                        maxCoeff = variable.lowerBound * 3
                        auxUb = Double.POSITIVE_INFINITY
                    } else if (variable.lowerBound == Double.NEGATIVE_INFINITY) {
                        // (max*3)*aux <= variable <= max*aux; aux any integer
                        minCoeff = variable.upperBound * 3
                        maxCoeff = variable.upperBound
                        auxUb = Double.POSITIVE_INFINITY
                    } else {
                        // min*aux <= variable <= max*aux; aux binary
                        minCoeff = variable.lowerBound
                        maxCoeff = variable.upperBound
                        auxUb = 1.0
                    }
                    val aux: MPVariable = solver.makeIntVar(0.0, auxUb, "${variable.name}_aux")
                    // min*aux <= variable
                    // variable - min*aux >= 0
                    solver.makeConstraint(0.0, Double.POSITIVE_INFINITY).apply {
                        setCoefficient(mpVariable, 1.0)
                        setCoefficient(aux, -minCoeff)
                    }
                    // variable <= max*aux
                    // variable - max*aux <= 0
                    solver.makeConstraint(Double.NEGATIVE_INFINITY, 0.0).apply {
                        setCoefficient(mpVariable, 1.0)
                        setCoefficient(aux, -maxCoeff)
                    }
                    mpVariable
                }
            }
        }
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
