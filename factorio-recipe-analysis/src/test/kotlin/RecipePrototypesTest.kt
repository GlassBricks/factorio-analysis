package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.be
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.instanceOf
import kotlin.test.assertNotNull

class RecipePrototypesTest : FreeSpec({
    val recipePrototypes by lazy { RecipePrototypes(SpaceAgeDataRaw) }
    "qualities" {
        recipePrototypes.qualities[0].name shouldBe "normal"
        recipePrototypes.qualities[1].name shouldBe "uncommon"
        recipePrototypes.qualities[4].name shouldBe "legendary"
        recipePrototypes.defaultQuality.name shouldBe "normal"
    }
    "allItems" {
        recipePrototypes.allItems shouldContainKey "inserter"
    }
    "beacons" {
        val beacon = assertNotNull( recipePrototypes.beacons["beacon"])

        assertNotNull(beacon.builtBy)
    }
})
