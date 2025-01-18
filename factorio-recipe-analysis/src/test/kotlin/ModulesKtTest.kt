package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

fun module(name: String) = Module(SpaceAgeDataRaw.module[name]!!, SpaceAge.defaultQuality)
class ModulesKtTest : FunSpec({
    context("module effect") {
        test("speed module quality") {
            val module = module("speed-module")
            val baseEffect = IntEffects(
                consumption = 50,
                speed = 20,
                quality = -10,
            )
            module.prototype.effect.toEffectInt(0) shouldBe baseEffect
            module.prototype.effect.toEffectInt(1) shouldBe baseEffect.copy(speed = 26)
            module.prototype.effect.toEffectInt(2) shouldBe baseEffect.copy(speed = 32)
            module.prototype.effect.toEffectInt(3) shouldBe baseEffect.copy(speed = 38)
            module.prototype.effect.toEffectInt(5) shouldBe baseEffect.copy(speed = 50)
        }
        test("quality module quality") {
            val name = "quality-module-3"
            val module = module(name)
            val baseEffect = IntEffects(
                speed = -5,
                quality = 25,
            )
            module.prototype.effect.toEffectInt(0) shouldBe baseEffect
            module.prototype.effect.toEffectInt(1) shouldBe baseEffect.copy(quality = 32)
            module.prototype.effect.toEffectInt(2) shouldBe baseEffect.copy(quality = 40)
            module.prototype.effect.toEffectInt(3) shouldBe baseEffect.copy(quality = 47)
            module.prototype.effect.toEffectInt(5) shouldBe baseEffect.copy(quality = 62)
        }
        test("prod module quality") {
            val module = module("productivity-module")
            val baseEffect = IntEffects(
                consumption = +40,
                speed = -5,
                productivity = 4,
                pollution = 5,
            )
            module.prototype.effect.toEffectInt(0) shouldBe baseEffect
            module.prototype.effect.toEffectInt(1) shouldBe baseEffect.copy(productivity = 5)
            module.prototype.effect.toEffectInt(2) shouldBe baseEffect.copy(productivity = 6)
            module.prototype.effect.toEffectInt(3) shouldBe baseEffect.copy(productivity = 7)
            module.prototype.effect.toEffectInt(5) shouldBe baseEffect.copy(productivity = 10)
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

    context("getFinalMachineEffect") {
        val speed1 = SpaceAge.modules["speed-module"]!!
        val speed2 = SpaceAge.modules["speed-module-2"]!!
        val quality3 = SpaceAge.modules["quality-module-3"]!!
        val prod2 = SpaceAge.modules["productivity-module-2"]!!
        val (_, uncommon, rare, epic, legendary) = SpaceAge.qualities
        test("single speed module") {
            val effects = getTotalMachineEffect(modules = listOf(speed1))
            effects.speedMultiplier shouldBe near(1.2)
            effects.prodMultiplier shouldBe 1.0
            effects.qualityChance shouldBe 0.0
        }
        test("quality and speed module") {
            val effects = getTotalMachineEffect(
                modules = listOf(speed1.withQuality(uncommon), quality3.withQuality(legendary))
            )
            effects.speedMultiplier shouldBe near(1.21)
            effects.prodMultiplier shouldBe 1.0
            effects.qualityChance shouldBe near(0.052)
        }
        test("speed module in beacon") {
            val effects = getTotalMachineEffect(
                listOf(quality3, quality3),
                listOf(SpaceAge.beacon.withModules(speed2))
            )
            effects.speedMultiplier shouldBe near(1.35)
            effects.prodMultiplier shouldBe 1.0
            effects.qualityChance shouldBe near(0.028)
            // test intentional rounding error shenanigans
            val effects2 = getTotalMachineEffect(
                listOf(quality3, quality3),
                listOf(SpaceAge.beacon.withModules(speed2, speed2))
            )
            effects2.speedMultiplier shouldBe near(1.8)
            effects2.prodMultiplier shouldBe 1.0
            effects2.qualityChance shouldBe near(0.005)
        }
        test("complex") {
            val effects = getTotalMachineEffect(
                listOf(
                    speed1.withQuality(uncommon),
                    quality3.withQuality(legendary),
                    prod2.withQuality(rare)
                ),
                listOf(SpaceAge.beacon.withModules(speed2.withQuality(epic)))
            )
            effects.speedMultiplier shouldBe near(1.96)
            effects.prodMultiplier shouldBe near(1.09)
            effects.qualityChance shouldBe near(0.03)
        }
    }
})
