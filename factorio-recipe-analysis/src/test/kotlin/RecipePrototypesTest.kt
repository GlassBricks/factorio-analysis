package me.glassbricks.factorio.recipes

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import kotlin.test.assertNotNull

class RecipePrototypesTest : FreeSpec({
    "qualities" {
        SpaceAge.qualities shouldHaveSize 5
        val (normal, uncommon, rare, epic, legendary) = SpaceAge.qualities
        normal.name shouldBe "normal"
        uncommon.name shouldBe "uncommon"
        rare.name shouldBe "rare"
        epic.name shouldBe "epic"
        legendary.name shouldBe "legendary"
    }
    "allItems" {
        SpaceAge.allItems shouldContainKey "inserter"
    }
    "beacons" {
        val beacon = assertNotNull(SpaceAge.beacons["beacon"])

        assertNotNull(beacon.builtBy)
    }

})
