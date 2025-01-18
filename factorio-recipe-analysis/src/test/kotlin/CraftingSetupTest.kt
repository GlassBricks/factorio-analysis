package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.Rate
import glassbricks.recipeanalysis.rateVector
import glassbricks.recipeanalysis.vector
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CraftingSetupTest : FunSpec({
    val asm2 = machine("assembling-machine-2")
    val asm3 = machine("assembling-machine-3")
    val foundry = machine("foundry")
    val prod1 = module("productivity-module")
    val speed1 = module("speed-module")
    val quality3 = module("quality-module-3")
    val (normal, uncommon, rare, epic, legendary) = SpaceAge.qualities
    test("doesn't accept prod modules in non prod recipe") {
        shouldThrow<IllegalArgumentException> {
            asm2.withModules(prod1).crafting(recipe("transport-belt"))
        }
    }
    test("basic recipe") {
        val setup = asm2.crafting(recipe("electronic-circuit"))
        setup.cycleInputs shouldBe mapOf(
            ingredient("copper-cable") to 3.0,
            ingredient("iron-plate") to 1.0,
        )
        setup.cycleOutputs shouldBe mapOf(
            ingredient("electronic-circuit") to 1.0,
        )
        setup.netRate shouldBe vector(
            ingredient("iron-plate") to -1.5,
            ingredient("copper-cable") to -4.5,
            ingredient("electronic-circuit") to 1.5,
        )

        val withSpeed = asm2
            .withModules(speed1)
            .crafting(recipe("electronic-circuit"))

        withSpeed.cycleTime.seconds shouldBe near(1 / 1.8)
        withSpeed.cycleInputs shouldBe setup.cycleInputs
        withSpeed.cycleOutputs shouldBe setup.cycleOutputs
        withSpeed.netRate.round1e6() shouldBe vector(
            ingredient("iron-plate") to -1.8,
            ingredient("copper-cable") to -5.4,
            ingredient("electronic-circuit") to 1.8,
        )

        val withProd = asm2
            .withModules(prod1)
            .crafting(recipe("electronic-circuit"))

        withProd.cycleTime.seconds shouldBe near(0.5 / 0.75 / 0.95)
        withProd.cycleInputs shouldBe setup.cycleInputs
        withProd.cycleOutputs.round1e6() shouldBe setup.cycleOutputs * 1.04

        withProd.netRate.round1e6() shouldBe vector<Rate, RealIngredient>(
            ingredient("iron-plate") to -1.5 * 0.95,
            ingredient("copper-cable") to -4.5 * 0.95,
            ingredient("electronic-circuit") to 1.482,
        ).round1e6()
    }


    test("with intrinsic prod") {
        val setup = foundry.crafting(recipe("transport-belt"))

        setup.cycleInputs shouldBe mapOf(
            ingredient("iron-plate") to 1.0,
            ingredient("iron-gear-wheel") to 1.0,
        )
        setup.cycleOutputs shouldBe mapOf(
            ingredient("transport-belt") to 3.0,
        )
        setup.netRate shouldBe vector(
            ingredient("iron-plate") to -8.0,
            ingredient("iron-gear-wheel") to -8.0,
            ingredient("transport-belt") to 24.0
        )
    }

    test("crafting different quality") {
        val setup = asm2.crafting(recipe("electronic-circuit").withQuality(legendary))
        setup.cycleInputs shouldBe mapOf(
            ingredient("copper-cable").maybeWithQuality(legendary) to 3.0,
            ingredient("iron-plate").maybeWithQuality(legendary) to 1.0,
        )
        setup.cycleOutputs shouldBe mapOf(
            ingredient("electronic-circuit").maybeWithQuality(legendary) to 1.0,
        )
        setup.netRate shouldBe vector(
            ingredient("iron-plate").maybeWithQuality(legendary) to -1.5,
            ingredient("copper-cable").maybeWithQuality(legendary) to -4.5,
            ingredient("electronic-circuit").maybeWithQuality(legendary) to 1.5,
        )
    }

    test("Gambling!") {
        val setup = asm3.withModules(quality3.repeat(3))
            .crafting(recipe("iron-chest"))
        val probs = listOf(
            1 - 0.075,
            0.075 * 0.9,
            0.075 * 0.1 * 0.9,
            0.075 * 0.01 * 0.9,
            0.075 * 0.001
        )
        probs.sum() shouldBe near(1.0)
        val expected = probs.zip(SpaceAge.qualities) { prop, quality ->
            rateVector(item("iron-chest").withQuality(quality) to 1.0) * prop
        }.reduce { a, b -> a + b }
        setup.cycleOutputs.round1e6() shouldBe expected.round1e6()
        setup.netRate.round1e6() shouldBe
                ((expected - rateVector(item("iron-plate") to 8.0)) * (2.0 * 1.0625)).round1e6()
    }
    test("Gambling but I'm not legendary") {
        val setup2 = asm3.withModules(quality3.repeat(3))
            .crafting(
                recipe("iron-chest").withQuality(uncommon),
                ResearchConfig(maxQuality = epic)
            )
        val probs2 = listOf(
            1 - 0.075,
            0.075 * 0.9,
            0.075 * 0.1
        )
        probs2.sum() shouldBe near(1.0)
        val expected2 = probs2.zip(SpaceAge.qualities.drop(1)) { prop, quality ->
            rateVector(item("iron-chest").withQuality(quality) to 1.0) * prop
        }.reduce { a, b -> a + b }
        setup2.cycleOutputs.round1e6() shouldBe expected2.round1e6()
    }
    test("gambling with fluids") {
        val setup = foundry.crafting(
            recipe("casting-low-density-structure")
                .withQuality(epic)
        )
        val plasticRate = 4.0 / 3
        setup.netRate.round1e6() shouldBe rateVector(
            item("plastic-bar").withQuality(epic) to -plasticRate,
            fluid("molten-iron") to -plasticRate * 16,
            fluid("molten-copper") to -plasticRate * 50,
            item("low-density-structure").withQuality(epic) to plasticRate / 5 * 1.5,
        ).round1e6()
    }
    test("gambling legendary quality should do nothing") {
        val setup = asm3.withModules(quality3.repeat(4))
            .crafting(recipe("iron-chest").withQuality(legendary))

        setup.cycleOutputs.round1e6() shouldBe rateVector(item("iron-chest").withQuality(legendary) to 1.0)
    }
})

private operator fun <E> List<E>.times(i: Int): List<E> = List(i) { this }.flatten()
private fun <E> E.repeat(i: Int): List<E> = List(i) { this }
