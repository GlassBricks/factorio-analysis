package glassbricks.recipeanalysis

import glassbricks.recipeanalysis.lp.*
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class OrToolsLpTest : FreeSpec({
    var solver by createEach { OrToolsLpSolver("GLOP") }
    "basic lp test" {
        val (xv, yv) = "xy".map { solver.addPositiveVariable(name = it.toString()) }
        val x = uvec(xv)
        val y = uvec(yv)
        solver += listOf(
            x + 2 * y leq 14.0,
            3 * x - y geq 0.0,
            x - y leq 2.0,
        )
        solver.objective.set(x + y, maximize = true)
        val result = solver.solve()
        result.status shouldBe LpResultStatus.Optimal
        val expectedSolution = vectorOfWithUnits<Unit, _>(xv to 6.0, yv to 4.0)
        val assignment = result.solution?.assignment ?: fail("no solution")
        if (!assignment.closeTo(expectedSolution, 1e-6)) {
            fail("Solution $assignment is not close to $expectedSolution")
        }
    }
    "can have negative objective" {
        val x = solver.addVariable(name = "x", lowerBound = -100.0, upperBound = 100.0)
        val xv = uvec(x)
        solver += listOf(xv geq -1.0)
        solver.objective.set(xv, maximize = false)
        val result = solver.solve()
        result.status shouldBe LpResultStatus.Optimal
        result.solution!!.objectiveValue shouldBe -1.0
    }
    "unbounded solution" {
        solver = OrToolsLpSolver("CBC")
        val x = solver.addPositiveVariable(name = "x")
        val xv = uvec(x)
        solver += listOf(xv geq 0.0)
        solver.objective.set(xv, maximize = true)
        val result = solver.solve()
        result.status shouldBe LpResultStatus.Unbounded
    }
    "error if setting integral variable on solver that doesn't support it" {
        shouldThrow<IllegalArgumentException> {
            solver.addPositiveVariable(name = "x", type = VariableType.Integer)
        }
    }
    "integral" {
        // x >= 0.5; minimize x
        solver = OrToolsLpSolver("SCIP")
        val x = solver.addPositiveVariable(name = "x", type = VariableType.Integer)
        val xv = uvec(x)
        solver += listOf(xv geq 0.5)
        solver.objective.set(xv, maximize = false)
        val result = solver.solve()
        result.status shouldBe LpResultStatus.Optimal
        result.solution!!.assignment[x] shouldBe 1.0
    }
    "semi-continuous".config(enabled = false) - {
        "zero" {
            val x =
                solver.addVariable(
                    name = "x",
                    type = VariableType.SemiContinuous,
                    lowerBound = 0.2,
                    upperBound = Double.POSITIVE_INFINITY
                )
            val xv = uvec(x)
            solver.objective.set(xv, maximize = false)
            val result = solver.solve()
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.0
        }

        "at upper bound" {
            val x =
                solver.addVariable(name = "x", type = VariableType.SemiContinuous, lowerBound = 0.2, upperBound = 0.5)
            val xv = uvec(x)
            solver.objective.set(xv, maximize = true)
            val result = solver.solve()
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.5
        }

        "at lower bound" {
            val x =
                solver.addVariable(name = "x", type = VariableType.SemiContinuous, lowerBound = 0.2, upperBound = 0.5)
            val xv = uvec(x)
            solver += listOf(xv geq 0.01)
            solver.objective.set(xv, maximize = false)
            val result = solver.solve()
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.2
        }

        // case 3: add constraint x > 0.01; x = 0.1
        "at lower bound, no upper bound" {
            val x = solver.addVariable(
                name = "x",
                type = VariableType.SemiContinuous,
                lowerBound = 0.2,
                upperBound = Double.POSITIVE_INFINITY
            )
            val xv = uvec(x)
            solver += listOf(xv geq 0.01)
            solver.objective.set(xv, maximize = false)
            val result = solver.solve()
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.2
        }

        "negative, at upper bound, no lower bound" {
            val x = solver.addVariable(
                name = "x",
                type = VariableType.SemiContinuous,
                upperBound = -0.2,
                lowerBound = Double.NEGATIVE_INFINITY,
            )
            val xv = uvec(x)
            solver += listOf(xv leq -0.01)
            solver.objective.set(xv, maximize = true)
            val result = solver.solve()
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe -0.2
        }

        "upper bound < lower bound, but zero valid" {
            val x =
                solver.addVariable(name = "x", type = VariableType.SemiContinuous, lowerBound = 0.5, upperBound = 0.2)
            val xv = uvec(x)
            solver.objective.set(xv, maximize = true)
            val result = solver.solve()
            result.status shouldBe LpResultStatus.Optimal
            result.solution!!.assignment[x] shouldBe 0.0
        }
    }
})
