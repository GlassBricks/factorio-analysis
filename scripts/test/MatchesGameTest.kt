package scripts

import glassbricks.factorio.recipes.*
import glassbricks.recipeanalysis.vectorOf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class MatchesGameTest : StringSpec({
    this as MatchesGameTest
    "test" {
        val f1 = foundry.withModules(fill = speedModule)
        f1.finalCraftingSpeed shouldBe beNear(7.2)

        f1.getBuildCost(prototypes) shouldBe vectorOf(
            foundry.item() to 1.0,
            speedModule to 4.0,
        )

        val f2 = foundry.withModules(
            fill = speedModule,
            beacons = listOf(
                beacon.withModules(speedModule3 * 2)
            )
        )
        f2.finalCraftingSpeed shouldBe beNear(13.2)
        f2.getBuildCost(prototypes).toMap() shouldBe vectorOf(
            foundry.item() to 1.0,
            speedModule to 4.0,
            beacon.item() to 1.0,
            speedModule3 to 2.0
        ).toMap()
    }
}), FactorioPrototypesScope by SpaceAge

fun beNear(number: Double, epsilon: Double = 1e-6): Matcher<Number> = object : Matcher<Number> {
    override fun test(value: Number) = object : MatcherResult {
        override fun passed(): Boolean = abs(value.toDouble() - number) <= epsilon
        override fun failureMessage(): String = "%f should be near %f (epsilon=%.1e)".format(value, number, epsilon)
        override fun negatedFailureMessage(): String =
            "%f should not be near %f (epsilon=%.1e)".format(value, number, epsilon)
    }
}
