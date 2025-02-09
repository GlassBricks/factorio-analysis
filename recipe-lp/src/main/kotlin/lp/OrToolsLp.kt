package glassbricks.recipeanalysis.lp

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import glassbricks.recipeanalysis.buildVector
import glassbricks.recipeanalysis.component1
import glassbricks.recipeanalysis.component2

class OrToolsLp(val solverId: String? = null) : LpSolver {
    class Result(
        override val status: LpResultStatus,
        override val bestBound: Double,
        override val solution: LpSolution?,
    ) : LpResult

    override fun solve(problem: LpProblem, options: LpOptions): Result {
        return createLpSolver(problem, options).solve()
    }

    inner class IncrementalSolver(
        val solver: MPSolver,
        val options: LpOptions,
        val variables: Set<Variable>,
    ) {
        fun solve(): Result {
            if (options.enableLogging) {
                println("Starting solve")
                println(solver.solverVersion())
            }
            val resultStatus = solver.solve()
            return createResult(resultStatus, solver, variables, options)
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

        val auxVariables = mutableMapOf<Variable, MPVariable>()
        for (variable in variables) {
            val mpVariable = when (variable.type) {
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
                    val mpVariable = solver.makeVar(
                        variable.lowerBound.coerceAtMost(0.0),
                        variable.upperBound.coerceAtLeast(0.0),
                        false,
                        variable.name
                    )
                    val minCoeff: Double
                    val maxCoeff: Double
                    val auxUb: Double
                    // create auxiliary variable
                    // min*aux <= variable <= max*aux
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
                    auxVariables[variable] = aux
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
            variable.realizedVariable = mpVariable
        }
        for (constraint in constraints) {
            val ct = solver.makeConstraint(0.0, 0.0)
            for ((variable, coefficient) in constraint.lhs) {
                ct.setCoefficient(variable.realizedVariable as MPVariable, coefficient)
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
            val mpVariable = variable.realizedVariable as MPVariable
            mpObjective.setCoefficient(mpVariable, coefficient)
        }
        mpObjective.setOffset(objective.constant)

        setSolverOptions(solver, options)

        return IncrementalSolver(
            solver = solver,
            options = options,
            variables = variables
        )
    }

    private fun setSolverOptions(
        solver: MPSolver,
        options: LpOptions,
    ) {
        solver.setTimeLimit(options.timeLimit.inWholeMilliseconds)
        solver.setNumThreads(options.numThreads)
        if (options.enableLogging) {
            solver.enableOutput()
        }
    }

    private fun createResult(
        resultStatus: MPSolver.ResultStatus?,
        solver: MPSolver,
        variables: Set<Variable>,
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

            val assignment = buildVector {
                for (variable in variables) {
                    val value = (variable.realizedVariable as MPVariable).solutionValue()
                    if (value !in -epsilon..epsilon) {
                        this[variable] = value
                    }
                }
            }
            val objective = solver.objective().value()
            LpSolution(assignment, objective)
        }
        val bestBound = solver.objective().bestBound()
        return Result(status, bestBound, solution)
    }
}
