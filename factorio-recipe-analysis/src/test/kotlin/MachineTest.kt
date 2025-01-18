package glassbricks.factorio.recipes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MachineTest : FunSpec({
    val (normal, uncommon, rare, epic, legendary) = SpaceAge.qualities
    test("assembling machine 2 and modules") {
        val asm2 = SpaceAge.machines["assembling-machine-2"]!!
        asm2.prototype.name shouldBe "assembling-machine-2"

        asm2.baseCraftingSpeed shouldBe 0.75
        asm2.appliedEffects shouldBe EffectInt()

        asm2.withQuality(uncommon).baseCraftingSpeed shouldBe near(0.975)
        asm2.withQuality(legendary).baseCraftingSpeed shouldBe near(1.875)

        asm2.withQuality(uncommon).appliedEffects shouldBe EffectInt()

        val speed1 = SpaceAge.modules["speed-module"]!!
        asm2.withModules(speed1).baseCraftingSpeed shouldBe 0.75
        asm2.withModules(speed1).appliedEffects shouldBe speed1.effect
    }
    test("em plant and modules") {
        val plant = SpaceAge.machines["electromagnetic-plant"]!!

        plant.prototype.name shouldBe "electromagnetic-plant"
        plant.baseCraftingSpeed shouldBe 2.0
        plant.appliedEffects shouldBe EffectInt(productivity = 50)

        plant.withQuality(uncommon).baseCraftingSpeed shouldBe near(2.6)
        plant.withQuality(uncommon).appliedEffects shouldBe plant.appliedEffects

        val speed1 = SpaceAge.modules["speed-module"]!!
        plant.withModules(speed1).baseCraftingSpeed shouldBe 2.0
        plant.withModules(speed1).appliedEffects shouldBe plant.appliedEffects + speed1.effect
    }
})
