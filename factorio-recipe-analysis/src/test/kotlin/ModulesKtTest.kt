package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ModulesKtTest : FunSpec({
    context("module effect") {
        test("speed module quality") {
            val module = Module(SpaceAgeDataRaw.module["speed-module"]!!)

            val baseEffect = EffectInt(
                consumption = 50,
                speed = 20,
                quality = -10,
            )
            module.effect(0) shouldBe baseEffect
            module.effect(1) shouldBe baseEffect.copy(speed = 26)
            module.effect(2) shouldBe baseEffect.copy(speed = 32)
            module.effect(3) shouldBe baseEffect.copy(speed = 38)
            module.effect(5) shouldBe baseEffect.copy(speed = 50)
        }
        test("quality module quality") {
            val module = Module(SpaceAgeDataRaw.module["quality-module-3"]!!)
            val baseEffect = EffectInt(
                speed = -5,
                quality = 25,
            )
            module.effect(0) shouldBe baseEffect
            module.effect(1) shouldBe baseEffect.copy(quality = 32)
            module.effect(2) shouldBe baseEffect.copy(quality = 40)
            module.effect(3) shouldBe baseEffect.copy(quality = 47)
            module.effect(5) shouldBe baseEffect.copy(quality = 62)
        }
        test("prod module quality") {
            val module = Module(SpaceAgeDataRaw.module["productivity-module"]!!)
            val baseEffect = EffectInt(
                consumption = +40,
                speed = -5,
                productivity = 4,
                pollution = 5,
            )
            module.effect(0) shouldBe baseEffect
            module.effect(1) shouldBe baseEffect.copy(productivity = 5)
            module.effect(2) shouldBe baseEffect.copy(productivity = 6)
            module.effect(3) shouldBe baseEffect.copy(productivity = 7)
            module.effect(5) shouldBe baseEffect.copy(productivity = 10)
        }
    }
    test("beacon acceptsModule") {
        val beacon = Beacon(SpaceAgeDataRaw.beacon.values.first(), null)
        fun module(name: String) = Module(SpaceAgeDataRaw.module[name]!!)

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
            val effects = getTotalMachineEffect(listOf(speed1))
            effects.speedMultiplier shouldBe near(1.2)
            effects.productivityMultiplier shouldBe 1.0
            effects.qualityChance shouldBe 0.0
        }
        test("quality and speed module") {
            val effects = getTotalMachineEffect(
                listOf(speed1.withQuality(uncommon), quality3.withQuality(legendary))
            )
            effects.speedMultiplier shouldBe near(1.21)
            effects.productivityMultiplier shouldBe 1.0
            effects.qualityChance shouldBe near(0.052)
        }
        test("speed module in beacon") {
            val effects = getTotalMachineEffect(
                listOf(quality3, quality3),
                listOf(SpaceAge.beacon.withModules(speed2))
            )
            effects.speedMultiplier shouldBe near(1.35)
            effects.productivityMultiplier shouldBe 1.0
            effects.qualityChance shouldBe near(0.028)
            // test intentional rounding error shenanigans
            val effects2 = getTotalMachineEffect(
                listOf(quality3, quality3),
                listOf(SpaceAge.beacon.withModules(speed2, speed2))
            )
            effects2.speedMultiplier shouldBe near(1.8)
            effects2.productivityMultiplier shouldBe 1.0
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
            effects.productivityMultiplier shouldBe near(1.09)
            effects.qualityChance shouldBe near(0.03)
        }
    }
})
