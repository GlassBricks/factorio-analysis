package glassbricks.recipeanalysis

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.beGreaterThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

fun recipe(
    name: String,
    vararg inOut: Pair<Ingredient, Double>,
    time: Double,
    additionalCosts: Vector<Symbol>? = null,
): LpProcess = LpProcess(
    object : Process {
        override val netRate: IngredientRate = vector(inOut.toMap()) / Time(time)
        override fun toString(): String = name
    },
    additionalCosts = additionalCosts ?: emptyVector(),
)

class RecipeSolverKtTest : StringSpec({
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
        val crudeInput = Input(crudeOil, 0.0, 100.0)
        val waterInput = Input(water, 0.0)
        val solidFuelOutput = Output(solidFuel, 100.0, 0.0)
        val recipes = listOf(
            advancedOil,
            basicOil,
            heavyCracking,
            lightCracking,
            solidHeavy,
            solidLight,
            solidPetrol,
            crudeInput,
            waterInput,
            solidFuelOutput,
        )
        val lp = RecipeLp(recipes)
        val result = lp.solve()
        result.status shouldBe LpResultStatus.Optimal
        val usage = result.solution?.recipes ?: fail("no usage")
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
            additionalCosts = basisVec(abstractCost) * 2.0
        )
        // more efficient, but lower throughput
        val process2 =
            recipe(
                "process2",
                inputIng to -0.5,
                outputIng to 0.6,
                time = 1.0,
                additionalCosts = basisVec(abstractCost) * 2.0
            )

        val input = Input(inputIng, cost = 0.0, upperBound = 1.0)
        val output = Output(outputIng, weight = 100.0, lowerBound = 0.0)
        val costRestr =
            SymbolConstraint(basisVec<Symbol>(abstractCost).relaxKeyType(), ComparisonOp.Leq, 2.0.toDouble())

        val processes = listOf(process1, process2, input, output)

        // no constraint should use process2 * 2
        val result1 = RecipeLp(processes).solve()
        result1.status shouldBe LpResultStatus.Optimal
        val usage = result1.solution?.recipes ?: fail("no usage")
        usage[process2] shouldBe 2.0
        usage[process1] shouldBe 0.0
        usage[output] shouldBe 1.2

        // constraint should use process1 * 1
        val result2 = RecipeLp(processes, listOf(costRestr)).solve()
        result2.status shouldBe LpResultStatus.Optimal
        val usage2 = result2.solution?.recipes ?: fail("no usage")
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
            additionalCosts = basisVec(abstractCost)
        )
        val input = Input(inputIng, cost = 0.0, upperBound = 1.0)
        val output = Output(outputIng, weight = 100.0, lowerBound = 0.0)

        val processes = listOf(process1, process2, input, output)
        val symbolCosts = mapOf(abstractCost to 1e8) // make it prohibitively expensive

        val result = RecipeLp(processes, symbolCosts = symbolCosts).solve()

        result.status shouldBe LpResultStatus.Optimal
        println(result.lpSolution?.objective)
        val usage = result.solution?.recipes ?: fail("no usage")
        usage[process2] shouldBe 0.0
        usage[process1] shouldBe 1.0
    }
})
