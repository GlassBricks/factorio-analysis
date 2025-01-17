package me.glassbricks.factorio.recipes

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import kotlin.math.abs

fun beNear(number: Double, epsilon: Double = 1e-6): Matcher<Number> = object : Matcher<Number> {
    override fun test(value: Number) = object : MatcherResult {
        override fun passed(): Boolean = abs(value.toDouble() - number) <= epsilon
        override fun failureMessage(): String = "%f should be near %f (epsilon=%.1e)".format(value, number, epsilon)
        override fun negatedFailureMessage(): String =
            "%f should not be near %f (epsilon=%.1e)".format(value, number, epsilon)
    }
}

fun near(number: Double, epsilon: Double = 1e-6) = beNear(number, epsilon)
