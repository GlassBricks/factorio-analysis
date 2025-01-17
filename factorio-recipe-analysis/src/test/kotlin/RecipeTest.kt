package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import glassbricks.recipeanalysis.amountVector
import glassbricks.recipeanalysis.vector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

fun ingredient(name: String) = SpaceAge.ingredients[name]!!
class RecipeTest : FunSpec({
    context("recipes are correct") {

        test("iron gear") {
            val recipe = Recipe.fromPrototype(SpaceAgeDataRaw.recipe["iron-gear-wheel"]!!, SpaceAge)
            recipe.prototype.name shouldBe "iron-gear-wheel"
            recipe.ingredients shouldBe vector(ingredient("iron-plate") to 2.0)
            recipe.products shouldBe vector(ingredient("iron-gear-wheel") to 1.0)
            recipe.productsIgnoredFromProductivity shouldBe vector()
        }
        test("kovarex") {
            val recipe = Recipe.fromPrototype(SpaceAgeDataRaw.recipe["kovarex-enrichment-process"]!!, SpaceAge)
            recipe.prototype.name shouldBe "kovarex-enrichment-process"
            recipe.ingredients shouldBe vector(ingredient("uranium-235") to 40.0, ingredient("uranium-238") to 5.0)
            recipe.products shouldBe vector(ingredient("uranium-235") to 41.0, ingredient("uranium-238") to 2.0)
            recipe.productsIgnoredFromProductivity shouldBe vector(
                ingredient("uranium-235") to 40.0,
                ingredient("uranium-238") to 2.0
            )
        }
        test("belt recycling") {
            val recipe = Recipe.fromPrototype(SpaceAgeDataRaw.recipe["transport-belt-recycling"]!!, SpaceAge)
            recipe.ingredients shouldBe vector(ingredient("transport-belt") to 1.0)
            recipe.products shouldBe amountVector(
                ingredient("iron-plate") to 1.0,
                ingredient("iron-gear-wheel") to 1.0
            ) / 8

        }
    }

})
