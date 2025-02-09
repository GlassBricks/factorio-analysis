package glassbricks.factorio.recipes

import glassbricks.recipeanalysis.AnyVector
import glassbricks.recipeanalysis.component1
import glassbricks.recipeanalysis.component2
import glassbricks.recipeanalysis.mapValues
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import kotlin.math.abs
import kotlin.math.roundToInt

fun beNear(number: Double, epsilon: Double = 1e-6): Matcher<Number> = object : Matcher<Number> {
    override fun test(value: Number) = object : MatcherResult {
        override fun passed(): Boolean = abs(value.toDouble() - number) <= epsilon
        override fun failureMessage(): String = "%f should be near %f (epsilon=%.1e)".format(value, number, epsilon)
        override fun negatedFailureMessage(): String =
            "%f should not be near %f (epsilon=%.1e)".format(value, number, epsilon)
    }
}

fun <T, U> AnyVector<T, U>.round1e6(): AnyVector<T, U> = mapValues { (_, value) -> (value * 1e6).roundToInt() / 1e6 }

fun near(number: Double, epsilon: Double = 1e-6) = beNear(number, epsilon)
