package glassbricks.recipeanalysis

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class OrToolsLpTest : FreeSpec({
    "basic lp test" {
        val (xv, yv) = "xy".map { Variable(it.toString()) }
        val x = basisVec(xv)
        val y = basisVec(yv)
        val constraints = constraints {
            x + 2 * y leq 14.0
            3 * x - y geq 0.0
            x - y leq 2.0
        }
        val objective = Objective(x + y, maximize = true)
        val problem = LpProblem(constraints, objective)
        val result = OrToolsLp().solve(problem)
        result.status shouldBe LpResultStatus.Optimal
        val expectedSolution = vectorWithUnits<Unit, _>(xv to 6.0, yv to 4.0)
        val assignment = result.solution?.assignment?.let { vectorWithUnits<Unit, _>(it) } ?: fail("no solution")
        if (!assignment.closeTo(expectedSolution, 1e-6)) {
            fail("Solution $assignment is not close to $expectedSolution")
        }
    }
    "can have negative objective" {
        val x = Variable("x")
        val xv = basisVec(x)
        val constraints = constraints { xv geq -1.0 }
        val objective = Objective(xv, maximize = false)
        val problem = LpProblem(constraints, objective)
        val result = OrToolsLp().solve(problem)
        result.status shouldBe LpResultStatus.Optimal
        result.solution!!.objectiveValue shouldBe -1.0
    }
    "unbounded solution" {
        val x = Variable("x")
        val xv = basisVec(x)
        val constraints = constraints { xv geq 0.0 }
        val objective = Objective(xv, maximize = true)
        val problem = LpProblem(constraints, objective)
        val result = OrToolsLp("CLP").solve(problem)
        result.status shouldBe LpResultStatus.Unbounded
    }
    "integral" {
        // x >= 0.5; minimize x
        val x = Variable("x", type = VariableType.Integer)
        val xv = basisVec(x)
        val constraints = constraints { xv geq 0.5 }
        val objective = Objective(xv, maximize = false)
        val problem = LpProblem(constraints, objective)
        val result = OrToolsLp().solve(problem)
        result.status shouldBe LpResultStatus.Optimal
        result.solution!!.assignment[x] shouldBe 1.0
    }
    "semi-continuous" - {
        "zero" {
            val x = Variable("x", type = VariableType.SemiContinuous, lowerBound = 0.2)
            val xv = basisVec(x)
            val objective = Objective(xv, maximize = false)
            val problem = LpProblem(emptyList(), objective)
            val result = OrToolsLp().solve(problem)
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.0
        }

        "at upper bound" {
            val x = Variable("x", type = VariableType.SemiContinuous, lowerBound = 0.2, upperBound = 0.5)
            val xv = basisVec(x)
            val objective = Objective(xv, maximize = true)
            val problem = LpProblem(emptyList(), objective)
            val result = OrToolsLp().solve(problem)
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.5
        }

        "at lower bound" {
            val x = Variable("x", type = VariableType.SemiContinuous, lowerBound = 0.2, upperBound = 0.5)
            val xv = basisVec(x)
            val constraints = constraints { xv geq 0.01 }
            val objective = Objective(xv, maximize = false)
            val problem = LpProblem(constraints, objective)
            val result = OrToolsLp().solve(problem)
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.2
        }

        // case 3: add constraint x > 0.01; x = 0.1
        "at lower bound, no upper bound" {
            val x = Variable("x", type = VariableType.SemiContinuous, lowerBound = 0.2)
            val xv = basisVec(x)
            val constraints = constraints { xv geq 0.01 }
            val objective = Objective(xv, maximize = false)
            val problem = LpProblem(constraints, objective)
            val result = OrToolsLp().solve(problem)
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.2
        }

        "negative, at upper bound, no lower bound" {
            val x = Variable(
                "x",
                type = VariableType.SemiContinuous,
                upperBound = -0.2,
                lowerBound = Double.NEGATIVE_INFINITY
            )
            val xv = basisVec(x)
            val constraints = constraints { xv leq -0.01 }
            val objective = Objective(xv, maximize = true)
            val problem = LpProblem(constraints, objective)
            val result = OrToolsLp().solve(problem)
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe -0.2
        }

        "upper bound < lower bound, but zero valid" {
            val x = Variable("x", type = VariableType.SemiContinuous, lowerBound = 0.5, upperBound = 0.2)
            val xv = basisVec(x)
            val objective = Objective(xv, maximize = true)
            val problem = LpProblem(constraints = emptyList(), objective)
            val result = OrToolsLp().solve(problem)
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.0
        }
    }
})
