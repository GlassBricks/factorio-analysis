package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.emptyVector
import glassbricks.recipeanalysis.vectorOf
import glassbricks.recipeanalysis.vectorOfWithUnits
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RecipeTest : FunSpec({
    this as RecipeTest
    context("recipes are correct") {
        test("iron gear") {
            val recipe = recipe("iron-gear-wheel")
            recipe.prototype.name shouldBe "iron-gear-wheel"
            recipe.inputs shouldBe vectorOfWithUnits(item("iron-plate") to 2.0)
            recipe.outputs shouldBe vectorOfWithUnits(item("iron-gear-wheel") to 1.0)
            recipe.outputsToIgnoreProductivity shouldBe emptyVector()
        }
        test("kovarex") {
            val recipe = recipe("kovarex-enrichment-process")
            recipe.prototype.name shouldBe "kovarex-enrichment-process"
            recipe.inputs shouldBe vectorOfWithUnits(item("uranium-235") to 40.0, item("uranium-238") to 5.0)
            recipe.outputs shouldBe vectorOfWithUnits(item("uranium-235") to 41.0, item("uranium-238") to 2.0)
            recipe.outputsToIgnoreProductivity shouldBe vectorOfWithUnits(
                item("uranium-235") to 40.0,
                item("uranium-238") to 2.0
            )
        }
        test("belt recycling") {
            val recipe = recipe("transport-belt-recycling")
            recipe.inputs shouldBe vectorOfWithUnits(item("transport-belt") to 1.0)
            recipe.outputs shouldBe vectorOf(
                item("iron-plate") to 1.0,
                item("iron-gear-wheel") to 1.0
            ) / 8
        }

        test("legendary night vision equipment") {
            val legendary = SpaceAge.qualityMap["legendary"]!!
            val recipe = recipe("night-vision-equipment").withQuality(legendary)
            recipe.inputs shouldBe vectorOfWithUnits(
                item("steel-plate").maybeWithQuality(legendary) to 10.0,
                item("advanced-circuit").maybeWithQuality(legendary) to 5.0,
            )
            recipe.outputs shouldBe vectorOfWithUnits(
                item("night-vision-equipment").maybeWithQuality(legendary) to 1.0,
            )
        }
    }

    test("accepts recipe") {
        val asm2 = SpaceAge.craftingMachines["assembling-machine-2"]!!
        val foundry = SpaceAge.craftingMachines["foundry"]!!
        val emp = SpaceAge.craftingMachines["electromagnetic-plant"]!!
        val furnace = SpaceAge.craftingMachines["steel-furnace"]!!

        val ironGear = recipe("iron-gear-wheel")
        asm2.canProcess(ironGear) shouldBe true
        foundry.canProcess(ironGear) shouldBe false
        emp.canProcess(ironGear) shouldBe false
        furnace.canProcess(ironGear) shouldBe false

        val belts = recipe("transport-belt")
        asm2.canProcess(belts) shouldBe true
        foundry.canProcess(belts) shouldBe true
        emp.canProcess(belts) shouldBe false
        furnace.canProcess(belts) shouldBe false

        val copperCable = recipe("copper-cable")
        asm2.canProcess(copperCable) shouldBe true
        foundry.canProcess(copperCable) shouldBe false
        emp.canProcess(copperCable) shouldBe true
        furnace.canProcess(copperCable) shouldBe false

        val plateSmelting = recipe("iron-plate")
        asm2.canProcess(plateSmelting) shouldBe false
        foundry.canProcess(plateSmelting) shouldBe false
        emp.canProcess(plateSmelting) shouldBe false
        furnace.canProcess(plateSmelting) shouldBe true

        val plateCasting = recipe("casting-iron")
        asm2.canProcess(plateCasting) shouldBe false
        foundry.canProcess(plateCasting) shouldBe true
        emp.canProcess(plateCasting) shouldBe false
        furnace.canProcess(plateCasting) shouldBe false
    }
}), FactorioPrototypesScope {
    override val prototypes get() = SpaceAge
}
