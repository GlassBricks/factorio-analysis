package glassbricks.factorio.recipes.problem

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IngredientSearchTestKt : FunSpec({
    var nextIndex = 0; beforeEach { nextIndex = 0 }

    class Recipe(
        override val inputs: List<String>,
        override val outputs: List<String>,
    ) : AbstractRecipe<String> {
        val index = nextIndex++
    }

    test("basic") {
        val recipes = listOf(
            Recipe(listOf("A", "B"), listOf("C")), // producible by inputs
            Recipe(listOf("C", "D"), listOf("E")), // producible since C is, and D has no inputs
            Recipe(emptyList(), listOf("D")),
            Recipe(listOf("E", "F"), listOf("G", "H")), // not producible since F is not
            Recipe(emptyList(), listOf("G")) // but G is producible
        )

        val (items, producibleRecipes) = findProducibleRecipes(
            recipes,
            startingItems = listOf("A", "B"),
        )

        items shouldBe setOf("A", "B", "C", "D", "E", "G")

        producibleRecipes.map { it.index }.toSet() shouldBe setOf(0, 1, 2, 4)
    }
})
