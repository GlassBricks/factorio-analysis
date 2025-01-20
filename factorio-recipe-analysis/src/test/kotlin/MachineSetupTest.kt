package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MachineSetupTest : FunSpec({
    this as MachineSetupTest
    val asm2 = craftingMachine("assembling-machine-2")
    val asm3 = craftingMachine("assembling-machine-3")
    val foundry = craftingMachine("foundry")
    val prod1 = module("productivity-module")
    val speed1 = module("speed-module")
    val quality3 = module("quality-module-3")
    val (normal, uncommon, rare, epic, legendary) = SpaceAge.qualities
    test("withModules") {
        asm2.withModules() shouldBe asm2
        asm2.withModules(speed1) shouldBe MachineWithModules(
            asm2,
            ModuleSet(
                ModuleList(listOf(speed1 * 1)),
                BeaconList(emptyList())
            )
        )
    }
    test("crafting") {
        val beltRecipe = recipe("transport-belt")
        asm2.processing(beltRecipe) shouldBe MachineSetup(
            machine = asm2,
            process = beltRecipe,
        )

    }
    test("doesn't accept prod modules in non prod recipe") {
        asm2.withModules(prod1).processingOrNull(recipe("transport-belt")) shouldBe null
        shouldThrow<IllegalArgumentException> {
            asm2.withModules(prod1).processing(recipe("transport-belt"))
        }
    }
    test("basic recipe") {
        val setup = asm2.processing(recipe("electronic-circuit"))
        setup.cycleInputs shouldBe mapOf(
            item("copper-cable") to 3.0,
            item("iron-plate") to 1.0,
        )
        setup.cycleOutputs shouldBe mapOf(
            item("electronic-circuit") to 1.0,
        )
        setup.netRate shouldBe vector(
            item("iron-plate") to -1.5,
            item("copper-cable") to -4.5,
            item("electronic-circuit") to 1.5,
        )

        val withSpeed = asm2
            .withModules(speed1)
            .processing(recipe("electronic-circuit"))

        withSpeed.cycleTime.seconds shouldBe near(1 / 1.8)
        withSpeed.cycleInputs shouldBe setup.cycleInputs
        withSpeed.cycleOutputs shouldBe setup.cycleOutputs
        withSpeed.netRate.round1e6() shouldBe vector(
            item("iron-plate") to -1.8,
            item("copper-cable") to -5.4,
            item("electronic-circuit") to 1.8,
        )

        val withProd = asm2
            .withModules(prod1)
            .processing(recipe("electronic-circuit"))

        withProd.cycleTime.seconds shouldBe near(0.5 / 0.75 / 0.95)
        withProd.cycleInputs shouldBe setup.cycleInputs
        withProd.cycleOutputs.round1e6() shouldBe setup.cycleOutputs * 1.04

        withProd.netRate.round1e6() shouldBe vector<Rate, RealIngredient>(
            item("iron-plate") to -1.5 * 0.95,
            item("copper-cable") to -4.5 * 0.95,
            item("electronic-circuit") to 1.482,
        ).round1e6()
    }


    test("with intrinsic prod") {
        val setup = foundry.processing(recipe("transport-belt"))

        setup.cycleInputs shouldBe mapOf(
            item("iron-plate") to 1.0,
            item("iron-gear-wheel") to 1.0,
        )
        setup.cycleOutputs shouldBe mapOf(
            item("transport-belt") to 3.0,
        )
        setup.netRate shouldBe vector(
            item("iron-plate") to -8.0,
            item("iron-gear-wheel") to -8.0,
            item("transport-belt") to 24.0
        )
    }

    test("crafting different quality") {
        val setup = asm2.processing(recipe("electronic-circuit").withQuality(legendary))
        setup.cycleInputs shouldBe mapOf(
            item("copper-cable").maybeWithQuality(legendary) to 3.0,
            item("iron-plate").maybeWithQuality(legendary) to 1.0,
        )
        setup.cycleOutputs shouldBe mapOf(
            item("electronic-circuit").maybeWithQuality(legendary) to 1.0,
        )
        setup.netRate shouldBe vector(
            item("iron-plate").maybeWithQuality(legendary) to -1.5,
            item("copper-cable").maybeWithQuality(legendary) to -4.5,
            item("electronic-circuit").maybeWithQuality(legendary) to 1.5,
        )
    }

    test("Gambling!") {
        val setup = asm3.withModules(quality3.repeat(3))
            .processing(recipe("iron-chest"))
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
            .processing(
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
        val setup = foundry.processing(
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
            .processing(recipe("iron-chest").withQuality(legendary))

        setup.cycleOutputs.round1e6() shouldBe rateVector(item("iron-chest").withQuality(legendary) to 1.0)
    }
}), WithFactorioPrototypes {
    override val prototypes get() = SpaceAge
}

private fun <E> E.repeat(i: Int): List<E> = List(i) { this }
