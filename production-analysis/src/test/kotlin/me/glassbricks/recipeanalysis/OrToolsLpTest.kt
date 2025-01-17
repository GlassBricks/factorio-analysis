package glassbricks.recipeanalysis

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OrToolsLpTest : StringSpec({
    "basic lp test" {
        val (xv, yv) = "xy".map { Variable(it.toString()) }
        val x = basis(xv)
        val y = basis(yv)
        val constraints = listOf(
            x + 2 * y le 14.0,
            3 * x - y ge 0.0,
            x - y le 2.0
        )
        val objective = Objective(x + y, maximize = true)
        val problem = LpProblem(constraints, objective)
        val result = OrToolsLp().solveLp(problem)
        result.status shouldBe LpResultStatus.Optimal
        val expectedSolution = vector<Unit, _>(xv to 6.0, yv to 4.0)
        val assignment = result.solution?.assignment?.let { vector<Unit, _>(it) } ?: fail("no solution")
        if (!assignment.closeTo(expectedSolution, 1e-6)) {
            fail("Solution $assignment is not close to $expectedSolution")
        }
    }

})
