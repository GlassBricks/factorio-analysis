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
): RealRecipe = RealRecipe(
    object : RecipeRate {
        override val netRate: IngredientRate = amountVector(inOut.toMap()) / Time(time)
        override fun toString(): String = name
    }
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
        val solution = lp.solve()
        solution.lpResult.status shouldBe LpResultStatus.Optimal
        val usage = solution.recipeUsage ?: fail("no usage")
        usage[basicOil] shouldBe 0.0
        usage[solidHeavy] shouldBe 0.0
        usage[advancedOil] shouldBe 5.0
        usage[heavyCracking] shouldBe 1.25
        usage[lightCracking] shouldBe 0.0
        usage[crudeInput] shouldBe 100.0
        usage[waterInput] should beGreaterThan(0.0)
        usage[solidFuelOutput] should beGreaterThan(0.0)
    }
})
