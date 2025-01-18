package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import glassbricks.recipeanalysis.amountVector
import glassbricks.recipeanalysis.vector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

fun ingredient(name: String) = SpaceAge.ingredients[name]!!
fun item(name: String) = SpaceAge.items[name]!!
fun fluid(name: String) = SpaceAge.fluids[name]!!
fun recipe(name: String) =
    Recipe.fromPrototype(SpaceAgeDataRaw.recipe[name]!!, SpaceAge.defaultQuality, SpaceAge)

class RecipeTest : FunSpec({
    context("recipes are correct") {
        test("iron gear") {
            val recipe = recipe("iron-gear-wheel")
            recipe.prototype.name shouldBe "iron-gear-wheel"
            recipe.inputs shouldBe vector(ingredient("iron-plate") to 2.0)
            recipe.outputs shouldBe vector(ingredient("iron-gear-wheel") to 1.0)
            recipe.outputsToIgnoreProductivity shouldBe vector()
        }
        test("kovarex") {
            val recipe = recipe("kovarex-enrichment-process")
            recipe.prototype.name shouldBe "kovarex-enrichment-process"
            recipe.inputs shouldBe vector(ingredient("uranium-235") to 40.0, ingredient("uranium-238") to 5.0)
            recipe.outputs shouldBe vector(ingredient("uranium-235") to 41.0, ingredient("uranium-238") to 2.0)
            recipe.outputsToIgnoreProductivity shouldBe vector(
                ingredient("uranium-235") to 40.0,
                ingredient("uranium-238") to 2.0
            )
        }
        test("belt recycling") {
            val recipe = recipe("transport-belt-recycling")
            recipe.inputs shouldBe vector(ingredient("transport-belt") to 1.0)
            recipe.outputs shouldBe amountVector(
                ingredient("iron-plate") to 1.0,
                ingredient("iron-gear-wheel") to 1.0
            ) / 8
        }

        test("legendary night vision equipment") {
            val legendary = SpaceAge.qualitiesMap["legendary"]!!
            val recipe = recipe("night-vision-equipment").withQuality(legendary)
            recipe.inputs shouldBe vector(
                ingredient("steel-plate").maybeWithQuality(legendary) to 10.0,
                ingredient("advanced-circuit").maybeWithQuality(legendary) to 5.0,
            )
            recipe.outputs shouldBe vector(
                ingredient("night-vision-equipment").maybeWithQuality(legendary) to 1.0,
            )
        }
    }

    test("accepts recipe") {
        val asm2 = SpaceAge.craftingMachines["assembling-machine-2"]!!
        val foundry = SpaceAge.craftingMachines["foundry"]!!
        val emp = SpaceAge.craftingMachines["electromagnetic-plant"]!!
        val furnace = SpaceAge.craftingMachines["steel-furnace"]!!

        val ironGear = recipe("iron-gear-wheel")
        asm2.acceptsRecipe(ironGear) shouldBe true
        foundry.acceptsRecipe(ironGear) shouldBe false
        emp.acceptsRecipe(ironGear) shouldBe false
        furnace.acceptsRecipe(ironGear) shouldBe false

        val belts = recipe("transport-belt")
        asm2.acceptsRecipe(belts) shouldBe true
        foundry.acceptsRecipe(belts) shouldBe true
        emp.acceptsRecipe(belts) shouldBe false
        furnace.acceptsRecipe(belts) shouldBe false

        val copperCable = recipe("copper-cable")
        asm2.acceptsRecipe(copperCable) shouldBe true
        foundry.acceptsRecipe(copperCable) shouldBe false
        emp.acceptsRecipe(copperCable) shouldBe true
        furnace.acceptsRecipe(copperCable) shouldBe false

        val plateSmelting = recipe("iron-plate")
        asm2.acceptsRecipe(plateSmelting) shouldBe false
        foundry.acceptsRecipe(plateSmelting) shouldBe false
        emp.acceptsRecipe(plateSmelting) shouldBe false
        furnace.acceptsRecipe(plateSmelting) shouldBe true

        val plateCasting = recipe("casting-iron")
        asm2.acceptsRecipe(plateCasting) shouldBe false
        foundry.acceptsRecipe(plateCasting) shouldBe true
        emp.acceptsRecipe(plateCasting) shouldBe false
        furnace.acceptsRecipe(plateCasting) shouldBe false
    }
})
