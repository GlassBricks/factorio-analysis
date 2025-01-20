package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.amountVector
import glassbricks.recipeanalysis.vector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ModulesKtTest : FunSpec({
    this as ModulesKtTest
    val speed1 = module("speed-module")
    val speed2 = module("speed-module-2")
    val qual3 = module("quality-module-3")
    val prod1 = module("productivity-module")
    test("moduleList") {
        val moduleList1 = moduleList(2, prod1)
        moduleList1?.moduleCounts shouldBe listOf(ModuleCount(prod1, 1))

        val moduleList2 = moduleList(4, prod1, speed1, fill = qual3)
        moduleList2?.moduleCounts shouldBe listOf(
            ModuleCount(prod1, 1),
            ModuleCount(speed1, 1),
            ModuleCount(qual3, 2),
        )
    }
    context("module effect") {
        test("speed module quality") {
            val baseEffect = IntEffects(
                consumption = 50,
                speed = 20,
                quality = -10,
            )
            speed1.prototype.effect.toEffectInt(0) shouldBe baseEffect
            speed1.prototype.effect.toEffectInt(1) shouldBe baseEffect.copy(speed = 26)
            speed1.prototype.effect.toEffectInt(2) shouldBe baseEffect.copy(speed = 32)
            speed1.prototype.effect.toEffectInt(3) shouldBe baseEffect.copy(speed = 38)
            speed1.prototype.effect.toEffectInt(5) shouldBe baseEffect.copy(speed = 50)
        }
        test("quality module quality") {
            val baseEffect = IntEffects(
                speed = -5,
                quality = 25,
            )
            qual3.prototype.effect.toEffectInt(0) shouldBe baseEffect
            qual3.prototype.effect.toEffectInt(1) shouldBe baseEffect.copy(quality = 32)
            qual3.prototype.effect.toEffectInt(2) shouldBe baseEffect.copy(quality = 40)
            qual3.prototype.effect.toEffectInt(3) shouldBe baseEffect.copy(quality = 47)
            qual3.prototype.effect.toEffectInt(5) shouldBe baseEffect.copy(quality = 62)
        }
        test("prod module quality") {
            val baseEffect = IntEffects(
                consumption = +40,
                speed = -5,
                productivity = 4,
                pollution = 5,
            )
            prod1.prototype.effect.toEffectInt(0) shouldBe baseEffect
            prod1.prototype.effect.toEffectInt(1) shouldBe baseEffect.copy(productivity = 5)
            prod1.prototype.effect.toEffectInt(2) shouldBe baseEffect.copy(productivity = 6)
            prod1.prototype.effect.toEffectInt(3) shouldBe baseEffect.copy(productivity = 7)
            prod1.prototype.effect.toEffectInt(5) shouldBe baseEffect.copy(productivity = 10)
        }
    }
    val beacon = SpaceAge.beacon
    test("beacon acceptsModule") {

        beacon.acceptsModule(module("speed-module")) shouldBe true
        beacon.acceptsModule(module("efficiency-module")) shouldBe true
        beacon.acceptsModule(module("productivity-module")) shouldBe false
        beacon.acceptsModule(module("quality-module")) shouldBe false
    }

    test("acceptsModule") {

        beacon.acceptsModule(module("speed-module")) shouldBe true
        beacon.acceptsModule(module("efficiency-module")) shouldBe true
        beacon.acceptsModule(module("productivity-module")) shouldBe false
        beacon.acceptsModule(module("quality-module")) shouldBe false
    }

    context("adding machine effects") {
        val speed2 = module("speed-module-2")
        val quality3 = module("quality-module-3")
        val prod2 = module("productivity-module-2")
        val (_, uncommon, rare, epic, legendary) = SpaceAge.qualities
        test("single speed module") {
            val effects = speed1.effects
            effects.speedMultiplier shouldBe near(1.2)
            effects.prodMultiplier shouldBe 1.0
            effects.qualityChance shouldBe 0.0
        }
        test("quality and speed module") {
            val effects = speed1.withQuality(uncommon) + quality3.withQuality(legendary)
            effects.speedMultiplier shouldBe near(1.21)
            effects.prodMultiplier shouldBe 1.0
            effects.qualityChance shouldBe near(0.052)
        }
        test("speed module in beacon") {
            val effects = (quality3 * 2) + BeaconList(beacon(speed2))
            effects.speedMultiplier shouldBe near(1.35)
            effects.prodMultiplier shouldBe 1.0
            effects.qualityChance shouldBe near(0.028)
            // test intentional rounding error shenanigans
            val effects2 = quality3 * 2 + BeaconList(beacon(speed2 * 2))
            effects2.speedMultiplier shouldBe near(1.8)
            effects2.prodMultiplier shouldBe 1.0
            effects2.qualityChance shouldBe near(0.005)
        }
        test("complex") {
            val effects =
                speed1.withQuality(uncommon) +
                        quality3.withQuality(legendary) +
                        prod2.withQuality(rare) + BeaconList(beacon(speed2.withQuality(epic)))
            effects.speedMultiplier shouldBe near(1.96)
            effects.prodMultiplier shouldBe near(1.09)
            effects.qualityChance shouldBe near(0.03)
        }
    }

    context("buildCosts") {
        test("modules") {
            (prod1 * 3).getBuildCost(SpaceAge) shouldBe vector(prod1 to 3.0)
            ModuleList(prod1 * 2, speed1 * 3).getBuildCost(SpaceAge) shouldBe vector(
                prod1 to 2.0,
                speed1 to 3.0,
            )
        }
        test("beacon") {
            val beaconItem = item("beacon")
            beacon(speed1 * 2).getBuildCost(SpaceAge) shouldBe vector(beaconItem to 1.0, speed1 to 2.0)
            beacon(speed1 * 2, sharing = 2.0).getBuildCost(SpaceAge) shouldBe amountVector(
                beaconItem to 1.0,
                speed1 to 2.0
            ) / 2.0

            BeaconList(
                beacon(speed1 * 2),
                beacon(speed2 * 2, sharing = 2.0),
            ).getBuildCost(SpaceAge) shouldBe amountVector(
                beaconItem to 1.5,
                speed1 to 2.0,
                speed2 to 1.0
            )
        }
        test("machine") {
            val asm2 = craftingMachine("assembling-machine-2")
            val machine = asm2.withModules(prod1 * 2, beacons = listOf(beacon(speed1 * 2)))
            machine.getBuildCost(SpaceAge) shouldBe vector(
                SpaceAge.itemOfOrNull(asm2) to 1.0,
                prod1 to 2.0,
                SpaceAge.itemOfOrNull(beacon) to 1.0,
                speed1 to 2.0,
            )
        }
    }
}), WithFactorioPrototypes {
    override val prototypes: FactorioPrototypes get() = SpaceAge
}
