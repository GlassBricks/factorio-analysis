package me.glassbricks.recipeanalysis

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OrToolsLpTest : StringSpec({
    "basic lp test" {
        val vars = "xy".map { Variable(it) }
        val x = basis('x')
        val y = basis('y')
        val constraints = listOf(
            x + 2 * y le 14.0,
            3 * x - y ge 0.0,
            x - y le 2.0
        )
        val objective = Objective(x + y, maximize = true)
        val result = OrToolsLp().solveLp(vars, constraints, objective)
        result.status shouldBe LpResultStatus.Optimal
        val expectedSolution = mapVector<Unit, _>('x' to 6.0, 'y' to 4.0)
        val assignment = result.solution?.assignment?.let { mapVector<Unit, _>(it) } ?: fail("no solution")
        if (!assignment.closeTo(expectedSolution, 1e-6)) {
            fail("Solution $assignment is not close to $expectedSolution")
        }
    }


})
