package glassbricks.recipeanalysis

import glassbricks.recipeanalysis.lp.*
import glassbricks.recipeanalysis.recipelp.Input
import glassbricks.recipeanalysis.recipelp.Output
import glassbricks.recipeanalysis.recipelp.ProductionLp
import glassbricks.recipeanalysis.recipelp.RealProcess
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.beGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

fun recipe(
    name: String,
    vararg inOut: Pair<Ingredient, Double>,
    time: Double,
    cost: Double = 0.0,
    additionalCosts: Vector<Symbol>? = null,
    costVariableConfig: VariableConfig? = null,
): RealProcess = RealProcess(
    object : Process {
        override val netRate: IngredientRate = inOut.toMap().toVector() / Time(time)
        override fun toString(): String = name
    },
    additionalCosts = additionalCosts ?: emptyVector(),
    costVariableConfig = costVariableConfig,
    variableConfig = VariableConfig(cost = cost),
)

class ProductionLpTest : StringSpec({
    "basic a->b" {
        val a = TestIngredient("a")
        val b = TestIngredient("b")
        val ab = recipe(
            "a->b",
            a to -1.0,
            b to 1.0,
            time = 1.0,
        )
        val aInput = Input(a, variableConfig = VariableConfig(cost = 10.0))
        val bOutput = Output(b, variableConfig = VariableConfig(lowerBound = 1.0))
        val lp = ProductionLp(
            inputs = listOf(aInput),
            outputs = listOf(bOutput),
            processes = listOf(ab),
        )
        val result = lp.solve()
        result.status shouldBe LpResultStatus.Optimal
        val usage = result.solution?.lpProcesses ?: fail("no usage")
        usage[ab] shouldBe 1.0
        usage[aInput] shouldBe 1.0
        usage[bOutput] shouldBe 1.0
    }

    "advanced oil solid fuel" {
        val crudeOil = TestIngredient("crude oil")
        val heavyOil = TestIngredient("heavy oil")
        val lightOil = TestIngredient("light oil")
        val petrol = TestIngredient("petroleum gas")
        val water = TestIngredient("water")
        val solidFuel = TestIngredient("solid fuel")

        val advancedOil = recipe(
            "advanced oil",
            crudeOil to -100.0,
            water to -50.0,
            heavyOil to 25.0,
            lightOil to 45.0,
            petrol to 55.0,
            time = 5.0,
        )
        val basicOil = recipe(
            "basic oil",
            crudeOil to -100.0,
            petrol to 45.0,
            time = 5.0,
        )
        val heavyCracking = recipe(
            "heavy cracking",
            heavyOil to -40.0,
            water to -30.0,
            lightOil to 30.0,
            time = 2.0,
        )
        val lightCracking = recipe(
            "light cracking",
            lightOil to -30.0,
            water to -30.0,
            petrol to 20.0,
            time = 2.0,
        )
        val solidHeavy = recipe(
            "solid heavy",
            heavyOil to -20.0,
            solidFuel to 1.0,
            time = 1.0,
        )
        val solidLight = recipe(
            "solid light",
            lightOil to -10.0,
            solidFuel to 1.0,
            time = 1.0,
        )
        val solidPetrol = recipe(
            "solid petrol",
            petrol to -10.0,
            solidFuel to 1.0,
            time = 1.0,
        )
        val crudeInput = Input(crudeOil, variableConfig = VariableConfig(cost = 0.0, upperBound = 100.0))
        val waterInput = Input(water, variableConfig = VariableConfig(cost = 0.0))
        val solidFuelOutput = Output(solidFuel, variableConfig = VariableConfig(cost = -100.0))
        val recipes = listOf(
            advancedOil,
            basicOil,
            heavyCracking,
            lightCracking,
            solidHeavy,
            solidLight,
            solidPetrol,
        )
        val lp = ProductionLp(
            inputs = listOf(crudeInput, waterInput),
            outputs = listOf(solidFuelOutput),
            processes = recipes,
        )
        val result = lp.solve()
        result.status shouldBe LpResultStatus.Optimal
        val usage = result.solution?.lpProcesses ?: fail("no usage")
        usage[basicOil] shouldBe 0.0
        usage[solidHeavy] shouldBe 0.0
        usage[advancedOil] shouldBe 5.0
        usage[heavyCracking] shouldBe 1.25
        usage[lightCracking] shouldBe 0.0
        usage[crudeInput] shouldBe 100.0
        usage[waterInput] should beGreaterThan(0.0)
        usage[solidFuelOutput] should beGreaterThan(0.0)
    }

    "constrained additional cost" {
        val inputIng = TestIngredient("input")
        val outputIng = TestIngredient("output")
        val abstractCost: Symbol = TestSymbol("abstract cost")
        val process1 = recipe(
            "process1",
            inputIng to -1.0,
            outputIng to 1.0,
            time = 1.0,
            additionalCosts = uvec(abstractCost) * 2.0
        )
        // more efficient, but lower throughput
        val process2 =
            recipe(
                "process2",
                inputIng to -0.5,
                outputIng to 0.6,
                time = 1.0,
                additionalCosts = uvec(abstractCost) * 2.0
            )

        val input = Input(inputIng, VariableConfig(cost = 0.0, upperBound = 1.0))
        val output = Output(outputIng, VariableConfig(cost = -100.0, lowerBound = 0.0))
        val costRestr =
            SymbolConstraint(uvec<Symbol>(abstractCost).relaxKeyType(), ComparisonOp.Leq, 2.0.toDouble())

        // no constraint should use process2 * 2
        val result1 = ProductionLp(
            inputs = listOf(input),
            outputs = listOf(output),
            processes = listOf(process1, process2)
        ).solve()
        result1.status shouldBe LpResultStatus.Optimal
        val usage = result1.solution?.lpProcesses ?: fail("no usage")
        usage[process2] shouldBe 2.0
        usage[process1] shouldBe 0.0
        usage[output] shouldBe 1.2

        // constraint should use process1 * 1
        val result2 = ProductionLp(
            inputs = listOf(input),
            outputs = listOf(output),
            processes = listOf(process1, process2),
            constraints = listOf(costRestr)
        ).solve()
        result2.status shouldBe LpResultStatus.Optimal
        val usage2 = result2.solution?.lpProcesses ?: fail("no usage")
        usage2[process1] shouldBe 1
        usage2[process2] shouldBe 0.0
        usage2[output] shouldBe 1.0
    }
    "high cost on additional symbol" {
        val inputIng = TestIngredient("input")
        val outputIng = TestIngredient("output")
        val abstractCost: Symbol = TestSymbol("abstract cost")
        val process1 = recipe(
            "process1",
            inputIng to -1.0,
            outputIng to 1.0,
            time = 1.0,
        )
        val process2 = recipe(
            "process2",
            inputIng to -0.5,
            outputIng to 0.6,
            time = 1.0,
            additionalCosts = uvec(abstractCost)
        )
        val input = Input(inputIng, VariableConfig(cost = 0.0, upperBound = 1.0))
        val output = Output(outputIng, VariableConfig(cost = -100.0, lowerBound = 0.0))

        val symbolConfigs = mapOf(abstractCost to VariableConfig(cost = 1e8)) // make it prohibitively expensive

        val result = ProductionLp(
            inputs = listOf(input),
            outputs = listOf(output),
            processes = listOf(process1, process2),
            symbolConfigs = symbolConfigs
        ).solve()

        result.status shouldBe LpResultStatus.Optimal
        println(result.lpSolution?.objectiveValue)
        val usage = result.solution?.lpProcesses ?: fail("no usage")
        usage[process2] shouldBe 0.0
        usage[process1] shouldBe 1.0
    }
    "use symbol config to force recipe to be used" {
        val inputIng = TestIngredient("input")
        val outputIng = TestIngredient("output")
        val abstractCost: Symbol = TestSymbol("abstract cost")
        val process = recipe(
            "process",
            inputIng to -1.0,
            outputIng to 1.0,
            time = 1.0,
            additionalCosts = uvec(abstractCost)
        )
        val input = Input(inputIng, VariableConfig(cost = 1.0))
        val symbolConfigs = mapOf(abstractCost to VariableConfig(lowerBound = 2.0))

        val result = ProductionLp(
            inputs = listOf(input),
            outputs = emptyList(),
            processes = listOf(process),
            symbolConfigs = symbolConfigs
        ).solve()
        result.status shouldBe LpResultStatus.Optimal
        val usage = result.solution?.lpProcesses ?: fail("no usage")
        usage[process] shouldBe 2.0
    }
    "use integral cost variable to raise costs" {
        val inputIng = TestIngredient("input")
        val outputIng = TestIngredient("output")
        val process = recipe(
            "process",
            inputIng to -1.0,
            outputIng to 1.0,
            time = 1.0,
            costVariableConfig = VariableConfig(cost = 1.0, type = VariableType.Integer)
        )
        val input = Input(inputIng, VariableConfig(cost = 0.0))
        val output = Output(outputIng, VariableConfig(lowerBound = 0.1))
        val result = ProductionLp(
            inputs = listOf(input),
            outputs = listOf(output),
            processes = listOf(process),
        ).solve(solver = OrToolsLpSolver("SCIP"))
        result.status shouldBe LpResultStatus.Optimal
        val usage = result.solution!!.processes.single().doubleValue
        usage shouldBe 0.1
        val cost = result.lpSolution?.objectiveValue
        cost shouldBe 1.0
    }

    "high surplus cost causes extra recipe to be used" {
        val a = TestIngredient("a")
        val b = TestIngredient("b")
        val c = TestIngredient("c")
        val cCompact = TestIngredient("c-compact")
        val processA = recipe(
            "a->b, c",
            a to -1.0,
            b to 1.0,
            c to 1.0,
            time = 1.0,
            cost = 1.0,
        )
        val cCompactify = recipe(
            "c->c-compact",
            c to -10.0,
            cCompact to 1.0,
            time = 1.0,
            cost = 1.0,
        )
        val input = Input(a, VariableConfig(cost = 0.0))
        val output = Output(b, VariableConfig(lowerBound = 1.0))
        val problem = ProductionLp(
            inputs = listOf(input),
            outputs = listOf(output),
            processes = listOf(processA, cCompactify),
            surplusCost = 10.0
        )
        val result = problem.solve()
        result.status shouldBe LpResultStatus.Optimal

        val solution = result.solution!!
        solution.lpProcesses[processA] shouldBe 1.0
        solution.lpProcesses[cCompactify] shouldBeGreaterThan 0.0

        solution.surpluses[c] shouldBe 0.0
        solution.surpluses[cCompact] shouldBeGreaterThan 0.0
    }
})
