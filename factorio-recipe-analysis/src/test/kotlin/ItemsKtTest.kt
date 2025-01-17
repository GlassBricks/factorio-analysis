package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.Effect
import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

class ItemsKtTest : FreeSpec({
    "getItem" - {
        "basic item" {
            val item = SpaceAgeDataRaw.item["inserter"]!!
            getItem(item) should beInstanceOf<BasicItem>()
        }
        "module" {
            val module = SpaceAgeDataRaw.module["speed-module"]!!
            getItem(module) should beInstanceOf<Module>()
        }
    }
    "speed module quality" {
        val module = Module(SpaceAgeDataRaw.module["speed-module"]!!)

        val baseEffect = Effect(
            consumption = +0.5f,
            speed = 0.2f,
            productivity = null,
            pollution = null,
            quality = -0.1f,
        )
        module.prototype.effect shouldBe baseEffect
        module.effect(0) shouldBe baseEffect
        module.effect(1) shouldBe baseEffect.copy(speed = 0.26f)
        module.effect(2) shouldBe baseEffect.copy(speed = 0.32f)
        module.effect(3) shouldBe baseEffect.copy(speed = 0.38f)
        module.effect(5) shouldBe baseEffect.copy(speed = 0.50f)
    }
    "quality module quality"{
        val module = Module(SpaceAgeDataRaw.module["quality-module-3"]!!)
        val baseEffect = Effect(
            consumption = null,
            speed = -0.05f,
            productivity = null,
            pollution = null,
            quality = 0.25f,
        )
        module.prototype.effect shouldBe baseEffect
        module.effect(0) shouldBe baseEffect
        module.effect(1) shouldBe baseEffect.copy(quality = 0.32f)
        module.effect(2) shouldBe baseEffect.copy(quality = 0.4f)
        module.effect(3) shouldBe baseEffect.copy(quality = 0.47f)
        module.effect(5) shouldBe baseEffect.copy(quality = 0.62f)
    }
    "prod module quality" {
        val module = Module(SpaceAgeDataRaw.module["productivity-module"]!!)
        val baseEffect = Effect(
            consumption = +0.4f,
            speed = -0.05f,
            productivity = 0.04f,
            pollution = +0.05f,
            quality = null
        )
        module.effect(0) shouldBe baseEffect
        module.effect(1) shouldBe baseEffect.copy(productivity = 0.05f)
        module.effect(2) shouldBe baseEffect.copy(productivity = 0.06f)
        module.effect(3) shouldBe baseEffect.copy(productivity = 0.07f)
        module.effect(5) shouldBe baseEffect.copy(productivity = 0.1f)
    }
})
