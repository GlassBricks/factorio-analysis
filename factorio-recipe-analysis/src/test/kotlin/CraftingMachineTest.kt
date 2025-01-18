package glassbricks.factorio.recipes

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

fun machine(name: String) = SpaceAge.craftingMachines[name]!!
class CraftingMachineTest : FunSpec({
    val (normal, uncommon, rare, epic, legendary) = SpaceAge.qualities
    val speed1 = SpaceAge.modules["speed-module"]!!
    test("assembling machine 2 and modules") {
        val asm2 = machine("assembling-machine-2")
        asm2.prototype.name shouldBe "assembling-machine-2"

        asm2.baseCraftingSpeed shouldBe 0.75
        asm2.effects shouldBe IntEffects()

        asm2.withQuality(uncommon).baseCraftingSpeed shouldBe near(0.975)
        asm2.withQuality(legendary).baseCraftingSpeed shouldBe near(1.875)

        asm2.withQuality(uncommon).effects shouldBe IntEffects()

        asm2.withModules(speed1).baseCraftingSpeed shouldBe 0.75
        asm2.withModules(speed1).effects shouldBe speed1.effects
    }
    test("em plant and modules") {
        val plant = machine("electromagnetic-plant")

        plant.prototype.name shouldBe "electromagnetic-plant"
        plant.baseCraftingSpeed shouldBe 2.0
        plant.effects shouldBe IntEffects(productivity = 50)

        plant.withQuality(uncommon).baseCraftingSpeed shouldBe near(2.6)
        plant.withQuality(uncommon).effects shouldBe plant.effects

        plant.withModules(speed1).baseCraftingSpeed shouldBe 2.0
        plant.withModules(speed1).effects shouldBe plant.effects + speed1.effects
    }
    test("withModulesOrNull should return null if too many modules") {
        val plant = machine("assembling-machine-2")
        plant.withModulesOrNull(speed1, speed1, speed1) shouldBe null
    }
})
