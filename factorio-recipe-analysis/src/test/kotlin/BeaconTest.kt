package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class BeaconTest : FreeSpec({
    "acceptsModule" {
        val beacon = Beacon(SpaceAgeDataRaw.beacon.values.first(), null)
        fun module(name: String) = Module(SpaceAgeDataRaw.module[name]!!)

        beacon.acceptsModule(module("speed-module")) shouldBe true
        beacon.acceptsModule(module("efficiency-module")) shouldBe true
        beacon.acceptsModule(module("productivity-module")) shouldBe false
        beacon.acceptsModule(module("quality-module")) shouldBe false
    }
})
