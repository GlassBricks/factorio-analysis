package glassbricks.recipeanalysis

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPSolverParameters
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
    val objectiveValue: Double,
)

interface LpResult {
    val status: LpResultStatus
    val bestBound: Double
    val solution: LpSolution?
}

class LpOptions(
    val timeLimit: Duration = 1.minutes,
    val checkSolution: Boolean = true,
    val epsilon: Double = 1e-5,
    val enableLogging: Boolean = false,
    val numThreads: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
)

interface LpSolver {
    fun solve(problem: LpProblem, options: LpOptions = LpOptions()): LpResult
    fun createIncrementalSolver(problem: LpProblem, options: LpOptions): IncrementalSolver<LpResult>
}

@Suppress("FunctionName")
fun DefaultLpSolver(): LpSolver = OrToolsLp()

class OrToolsLp(val solverId: String? = null) : LpSolver {
    class Result(
        override val status: LpResultStatus,
        override val bestBound: Double,
        override val solution: LpSolution?,
    ) : LpResult

    override fun createIncrementalSolver(
        problem: LpProblem,
        options: LpOptions,
    ): IncrementalSolver {
        return createLpSolver(problem, options)
    }

    override fun solve(problem: LpProblem, options: LpOptions): Result {
        return createLpSolver(problem, options).runFor(options.timeLimit)
    }

    inner class IncrementalSolver(
        val solver: MPSolver,
        val options: LpOptions,
        val mpVariables: Map<Variable, MPVariable>,
        val enableIncremental: Boolean = true,
    ) : AbstractIncrementalSolver<Result>() {
        override fun doSolveFor(duration: Duration): Pair<Result, Boolean> {
            solver.setTimeLimit(duration.inWholeMilliseconds)

            val solverParams = MPSolverParameters().apply {
                val incremental =
                    if (enableIncremental) MPSolverParameters.IncrementalityValues.INCREMENTALITY_ON else MPSolverParameters.IncrementalityValues.INCREMENTALITY_OFF
                setIntegerParam(MPSolverParameters.IntegerParam.INCREMENTALITY, incremental.swigValue())
            }

            val resultStatus = solver.solve(solverParams)

            var result = createResult(resultStatus, solver, mpVariables, options)
            val canSolveMore =
                when (result.status) {
                    LpResultStatus.Infeasible, LpResultStatus.Unbounded, LpResultStatus.Optimal -> false
                    else -> true
                }
            return result to canSolveMore
        }

    }

    private fun createLpSolver(problem: LpProblem, options: LpOptions): IncrementalSolver {
        Loader.loadNativeLibraries()
        val (constraints, objective) = problem
        val variables = buildSet {
            for (constraint in constraints) {
                addAll(constraint.lhs.keys)
            }
            addAll(objective.coefficients.keys)
        }
        val solverId = solverId ?: when {
            variables.all { it.type == VariableType.Integer } -> "SAT"
            variables.any { it.type != VariableType.Continuous } -> "SCIP"
            else -> "GLOP"
        }
        val solver = MPSolver.createSolver(solverId) ?: error("Solver not found")

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
        solver.setTimeLimit(options.timeLimit.inWholeMilliseconds)
        mpObjective.setOffset(objective.constant)

        solver.setNumThreads(options.numThreads)
        if (options.enableLogging) {
            solver.enableOutput()
        }

        return IncrementalSolver(solver, options, mpVariables)
    }

    private fun createResult(
        resultStatus: MPSolver.ResultStatus?,
        solver: MPSolver,
        mpVariables: Map<Variable, MPVariable>,
        options: LpOptions,
    ): Result {
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
        val hasSolution = status == LpResultStatus.Optimal || status == LpResultStatus.Feasible
        val epsilon = options.epsilon
        val solution = if (!hasSolution) null else {

            if (options.checkSolution) {
                solver.verifySolution(epsilon, true)
            }

            val assignment = mpVariables.mapValuesNotNull {
                it.value.solutionValue()
                    .let { if (it in -epsilon..epsilon) 0.0 else it }
            }
            val objective = solver.objective().value()
            LpSolution(vectorWithUnits(assignment), objective)
        }
        val bestBound = solver.objective().bestBound()
        return Result(status, bestBound, solution)
    }
}
