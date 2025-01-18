package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import glassbricks.recipeanalysis.amountVector
import glassbricks.recipeanalysis.vector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

fun ingredient(name: String) = SpaceAge.ingredients[name]!!
fun recipe(name: String) = Recipe.fromPrototype(SpaceAgeDataRaw.recipe[name]!!, SpaceAge.defaultQuality, SpaceAge)
class RecipeTest : FunSpec({
    context("recipes are correct") {
        test("iron gear") {
            val recipe = recipe("iron-gear-wheel")
            recipe.prototype.name shouldBe "iron-gear-wheel"
            recipe.ingredients shouldBe vector(ingredient("iron-plate") to 2.0)
            recipe.products shouldBe vector(ingredient("iron-gear-wheel") to 1.0)
            recipe.productsIgnoredFromProductivity shouldBe vector()
        }
        test("kovarex") {
            val recipe = recipe("kovarex-enrichment-process")
            recipe.prototype.name shouldBe "kovarex-enrichment-process"
            recipe.ingredients shouldBe vector(ingredient("uranium-235") to 40.0, ingredient("uranium-238") to 5.0)
            recipe.products shouldBe vector(ingredient("uranium-235") to 41.0, ingredient("uranium-238") to 2.0)
            recipe.productsIgnoredFromProductivity shouldBe vector(
                ingredient("uranium-235") to 40.0,
                ingredient("uranium-238") to 2.0
            )
        }
        test("belt recycling") {
            val recipe = recipe("transport-belt-recycling")
            recipe.ingredients shouldBe vector(ingredient("transport-belt") to 1.0)
            recipe.products shouldBe amountVector(
                ingredient("iron-plate") to 1.0,
                ingredient("iron-gear-wheel") to 1.0
            ) / 8
        }

        test("legendary night vision equipment") {
            val legendary = SpaceAge.quality("legendary")!!
            val recipe = recipe("night-vision-equipment").withQuality(legendary)
            recipe.ingredients shouldBe vector(
                ingredient("steel-plate").maybeWithQuality(legendary) to 10.0,
                ingredient("advanced-circuit").maybeWithQuality(legendary) to 5.0,
            )
            recipe.products shouldBe vector(
                ingredient("night-vision-equipment").maybeWithQuality(legendary) to 1.0,
            )
        }
    }

})
