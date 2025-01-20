package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.ItemID
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import kotlin.test.assertNotNull

class RecipePrototypesTest : FreeSpec({
    "qualities" {
        SpaceAge.qualities shouldHaveSize 5
        val expectedNames = listOf("normal", "uncommon", "rare", "epic", "legendary")
        SpaceAge.qualities.zip(expectedNames).forEach { (quality, expectedName) ->
            quality.prototype.name shouldBe expectedName
        }
        SpaceAge.qualities.zipWithNext().forEach { (current, next) ->
            current.nextQuality shouldBe next
        }
    }
    "items" {
        SpaceAge.items shouldContainKey ItemID("inserter")
    }
    "beacons" {
        assertNotNull(SpaceAge.beacons["beacon"])
    }
})
