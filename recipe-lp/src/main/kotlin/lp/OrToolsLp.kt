package glassbricks.recipeanalysis.lp

import com.google.ortools.Loader
import com.google.ortools.linearsolver.MPConstraint
import com.google.ortools.linearsolver.MPObjective
import com.google.ortools.linearsolver.MPSolver
import com.google.ortools.linearsolver.MPVariable
import glassbricks.recipeanalysis.buildVector
import glassbricks.recipeanalysis.relaxKeyType

class OrToolsLp(solverId: String = "GLOP") : LpSolver {
    init {
        Loader.loadNativeLibraries()
    }

    private val solver: MPSolver = MPSolver.createSolver(solverId) ?: error("Could not create solver $solverId")

    override val supportsIntegerPrograms: Boolean get() = "INTEGER" in solver.problemType().toString()

    private inner class VariableImpl(
        val variable: MPVariable,
        override val type: VariableType,
    ) : Variable {
        override val name: String get() = variable.name()
        override var lowerBound: Double
            get() = variable.lb()
            set(value) = variable.setLb(value)
        override var upperBound: Double
            get() = variable.ub()
            set(value) = variable.setUb(value)
        override var objectiveWeight: Double
            get() = solver.objective().getCoefficient(variable)
            set(value) = solver.objective().setCoefficient(variable, value)

        override fun toString(): String =
            "Variable(name=\"$name\", type=$type, lowerBound=$lowerBound, upperBound=$upperBound)"
    }

    private val _variables = mutableListOf<VariableImpl>()
    override val variables: List<Variable> get() = _variables

    override fun addVariable(
        lowerBound: Double,
        upperBound: Double,
        name: String,
        type: VariableType,
        cost: Double,
    ): Variable {
        val variable = when (type) {
            VariableType.Continuous -> solver.makeNumVar(lowerBound, upperBound, name)
            VariableType.Integer -> {
                require(supportsIntegerPrograms) { "Solver ${solver.solverVersion()} does not support integer programs" }
                solver.makeIntVar(lowerBound, upperBound, name)
            }

            VariableType.SemiContinuous -> TODO("SemiContinuous")
        }
        return VariableImpl(variable, type).also {
            objective[it] = cost
            _variables.add(it)
        }
    }

    private inner class ConstraintImpl(val constraint: MPConstraint) : Constraint {
        override val parent: LpSolver get() = this@OrToolsLp
        override var lb: Double
            get() = constraint.lb()
            set(value) = constraint.setLb(value)
        override var ub: Double
            get() = constraint.ub()
            set(value) = constraint.setUb(value)

        override fun get(variable: Variable): Double =
            constraint.getCoefficient((variable as VariableImpl).variable)

        override fun set(variable: Variable, value: Double) =
            constraint.setCoefficient((variable as VariableImpl).variable, value)
    }

    private val _constraints = mutableListOf<ConstraintImpl>()
    override val constraints: List<Constraint> get() = _constraints

    override fun addConstraint(lb: Double, ub: Double, name: String): Constraint {
        val constraint = solver.makeConstraint(lb, ub, name)
        return ConstraintImpl(constraint).also { _constraints.add(it) }
    }

    private inner class ObjectiveImpl(val objective: MPObjective) : Objective {
        override val parent: LpSolver get() = this@OrToolsLp

        override var maximize: Boolean
            get() = objective.maximization()
            set(value) {
                if (value) objective.setMaximization() else objective.setMinimization()
            }

        override fun set(variable: Variable, weight: Double) {
            objective.setCoefficient((variable as VariableImpl).variable, weight)
        }

        override fun get(variable: Variable): Double =
            objective.getCoefficient((variable as VariableImpl).variable)

    }

    override val objective: Objective = ObjectiveImpl(solver.objective())

    override fun solve(options: LpOptions): LpResult {
        setSolverOptions(solver, options)
        val resultStatus = solver.solve()
        return createResult(resultStatus, options)
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

    private fun createResult(resultStatus: MPSolver.ResultStatus?, options: LpOptions): LpResult {
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
            val assignment = buildVector(_variables.size) {
                for (variable in _variables) {
                    val value = variable.variable.solutionValue()
                    if (value !in -epsilon..epsilon) {
                        this[variable] = value
                    }
                }
            }
            val objective = solver.objective().value()
            LpSolution(assignment.relaxKeyType(), objective)
        }
        val bestBound = solver.objective().bestBound()
        return LpResult(status, solution, bestBound)
    }

}
