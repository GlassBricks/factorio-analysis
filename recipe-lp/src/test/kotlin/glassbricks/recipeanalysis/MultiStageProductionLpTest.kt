package glassbricks.recipeanalysis

import glassbricks.recipeanalysis.lp.LpResultStatus
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.eq
import glassbricks.recipeanalysis.recipelp.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MultiStageProductionLpTest : StringSpec({
    "should solve multi-stage problem" {
        // stage 1: a -> b recipe
        // stage 2: b -> c recipe
        // a input, c output
        val a = TestIngredient("a")
        val b = TestIngredient("b")
        val c = TestIngredient("c")
        val processA = recipe("a", a to -1.0, b to 1.0, time = 1.0)
        val processB = recipe("b", b to -1.0, c to 1.0, time = 1.0)
        val aIn = Input(a, variableConfig = VariableConfig(cost = 100.0))
        val bOut1 = Output(b, variableConfig = VariableConfig())
        val bIn2 = Input(b, variableConfig = VariableConfig())
        val cOut = Output(c, variableConfig = VariableConfig(lowerBound = 1.0))

        val stage1 = ProductionLp(processes = listOf(aIn, processA, bOut1)).toStage()
        val stage2 = ProductionLp(processes = listOf(bIn2, processB, cOut)).toStage()
        val bConstraint = uvec(stage1.ref(bOut1)) eq uvec(stage2.ref(bIn2))

        val problem = MultiStageProductionLp(
            stages = listOf(stage1, stage2),
            additionalConstraints = listOf(bConstraint)
        )
        val result = problem.solve()
        result.lpResult.status shouldBe LpResultStatus.Optimal
        val solutions = result.solutions!!
        val stage1Solution = solutions[stage1]!!
        stage1Solution.lpProcesses[aIn] shouldBe 1.0
        stage1Solution.lpProcesses[processA] shouldBe 1.0
        stage1Solution.lpProcesses[bOut1] shouldBe 1.0
        val stage2Solution = solutions[stage2]!!
        stage2Solution.lpProcesses[bIn2] shouldBe 1.0
        stage2Solution.lpProcesses[processB] shouldBe 1.0
        stage2Solution.lpProcesses[cOut] shouldBe 1.0
    }
})
