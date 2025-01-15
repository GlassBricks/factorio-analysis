package me.glassbricks.recipeanalysis

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


data class Variable<V>(
    val id: V,
    val lb: Double = Double.NEGATIVE_INFINITY,
    val ub: Double = Double.POSITIVE_INFINITY,
    val integer: Boolean = false
)

enum class ComparisonOp { LE, GE, EQ }
data class Constraint<V>(val lhs: Map<V, Double>, val op: ComparisonOp, val rhs: Double) {
    override fun toString(): String {
        val opStr = when (op) {
            ComparisonOp.LE -> "<="
            ComparisonOp.GE -> ">="
            ComparisonOp.EQ -> "=="
        }
        return lhs.entries.joinToString(
            prefix = "Constraint(",
            postfix = " $opStr $rhs)",
            separator = " + "
        ) { (variable, coefficient) -> "$variable * $coefficient" }
    }
}

infix fun <V> MapVector<V, Unit>.le(rhs: Double) = Constraint(this, ComparisonOp.LE, rhs)
infix fun <V> MapVector<V, Unit>.ge(rhs: Double) = Constraint(this, ComparisonOp.GE, rhs)
infix fun <V> MapVector<V, Unit>.eq(rhs: Double) = Constraint(this, ComparisonOp.EQ, rhs)

data class Objective<V>(
    val coefficients: Map<V, Double>,
    val constant: Double = 0.0,
    val maximize: Boolean = true
)

data class LpProblem<V>(
    val variables: List<Variable<V>>, val constraints: List<Constraint<V>>, val objective: Objective<V>
)

enum class LpResultStatus {
    Optimal,
    Infeasible,
    Unbounded,
    Feasible,
    Error
}

class LpSolution<V>(
    val assignment: Map<V, Double>,
    val objective: Double
)

interface LpResult<V> {
    val status: LpResultStatus
    val solution: LpSolution<V>?
}

interface LpSolver<V> {
    fun solveLp(problem: LpProblem<V>): LpResult<V>
}

class OrToolsLp<V>(val solverId: String = "GLOP", val timeLimit: Duration = 1.minutes) : LpSolver<V> {
    class Result<V>(
        val solver: MPSolver,
        override val status: LpResultStatus,
        variables: Map<V, MPVariable>,
    ) : LpResult<V> {
        override val solution: LpSolution<V>?

        init {
            val hasSolution = status == LpResultStatus.Optimal || status == LpResultStatus.Feasible
            solution = if (!hasSolution) null else {
                val assignment = variables.mapValues { it.value.solutionValue() }
                val objective = solver.objective().value()
                LpSolution(assignment, objective)
            }
        }
    }

    override fun solveLp(problem: LpProblem<V>): Result<V> {
        val (variables, constraints, objective) = problem
        Loader.loadNativeLibraries()
        val solver = MPSolver.createSolver(solverId) ?: error("Solver not found")
        solver.setTimeLimit(timeLimit.inWholeMilliseconds)

        val mpVariables = variables.associate { it.id to solver.makeNumVar(it.lb, it.ub, it.id.toString())!! }
        for (constraint in constraints) {
            val ct = solver.makeConstraint(0.0, 0.0)
            for ((variable, coefficient) in constraint.lhs) {
                val mpVariable = mpVariables[variable] ?: error("Variable in constraint not found")
                ct.setCoefficient(mpVariable, coefficient)
            }
            when (constraint.op) {
                ComparisonOp.LE -> ct.setBounds(Double.NEGATIVE_INFINITY, constraint.rhs)
                ComparisonOp.GE -> ct.setBounds(constraint.rhs, Double.POSITIVE_INFINITY)
                ComparisonOp.EQ -> ct.setBounds(constraint.rhs, constraint.rhs)
            }
        }

        val mpObjective = solver.objective()!!
        mpObjective.setOptimizationDirection(objective.maximize)
        for ((variable, coefficient) in objective.coefficients) {
            val mpVariable = mpVariables[variable] ?: error("Variable in objective not found")
            mpObjective.setCoefficient(mpVariable, coefficient)
        }
        mpObjective.setOffset(objective.constant)

        val status = when (solver.solve()) {
            MPSolver.ResultStatus.OPTIMAL -> LpResultStatus.Optimal
            MPSolver.ResultStatus.FEASIBLE -> LpResultStatus.Feasible
            MPSolver.ResultStatus.INFEASIBLE -> LpResultStatus.Infeasible
            MPSolver.ResultStatus.UNBOUNDED -> LpResultStatus.Unbounded
            null, MPSolver.ResultStatus.ABNORMAL,
            MPSolver.ResultStatus.MODEL_INVALID,
            MPSolver.ResultStatus.NOT_SOLVED -> LpResultStatus.Error
        }
        return Result(solver, status, mpVariables)
    }
}
