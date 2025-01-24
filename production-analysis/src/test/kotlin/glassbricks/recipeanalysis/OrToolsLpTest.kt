package glassbricks.recipeanalysis

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OrToolsLpTest : StringSpec({
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
        val result = OrToolsLp().solveLp(problem)
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
        val result = OrToolsLp().solveLp(problem)
        result.status shouldBe LpResultStatus.Optimal
        result.solution!!.objective shouldBe -1.0
    }
    "unbounded solution" {
        val x = Variable("x")
        val xv = basisVec(x)
        val constraints = constraints { xv geq 0.0 }
        val objective = Objective(xv, maximize = true)
        val problem = LpProblem(constraints, objective)
        val result = OrToolsLp("CLP").solveLp(problem)
        result.status shouldBe LpResultStatus.Unbounded
    }
    "integral" {
        // x >= 0.5; minimize x
        val x = Variable("x", integral = true)
        val xv = basisVec(x)
        val constraints = constraints { xv geq 0.5 }
        val objective = Objective(xv, maximize = false)
        val problem = LpProblem(constraints, objective)
        val result = OrToolsLp().solveLp(problem)
        result.status shouldBe LpResultStatus.Optimal
        result.solution!!.assignment[x] shouldBe 1.0
    }
})
