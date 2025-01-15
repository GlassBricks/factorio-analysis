package me.glassbricks.recipeanalysis

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.*

private fun mapVector(vararg pairs: Pair<String, Double>): MapVector<String, Nothing> {
    return mapVector(pairs.toMap())
}

class MapVectorTest : FreeSpec({
    "should remove 0 values on construction" {
        val mapVector = mapVector("a" to 1.0, "b" to 0.0, "c" to 2.0)
        mapVector shouldBe mapVector("a" to 1.0, "c" to 2.0)
    }
    "get" {
        val mapVector = mapVector("a" to 1.0, "b" to 2.0)
        mapVector["a"] shouldBe 1.0
        mapVector["b"] shouldBe 2.0
        mapVector["c"] shouldBe 0.0
    }
    "plus" {
        val mapVector1 = mapVector("a" to 1.0, "b" to 2.0, "z" to 3.0)
        val mapVector2 = mapVector("b" to 3.0, "c" to 4.0, "z" to -3.0)
        val result = mapVector1 + mapVector2
        result shouldBe mapVector("a" to 1.0, "b" to 5.0, "c" to 4.0)
    }
    "minus" {
        val mapVector1 = mapVector("a" to 1.0, "b" to 2.0, "z" to 3.0)
        val mapVector2 = mapVector("b" to 3.0, "c" to 4.0, "z" to 3.0)
        val result = mapVector1 - mapVector2
        result shouldBe mapVector("a" to 1.0, "b" to -1.0, "c" to -4.0)
    }
    "unaryMinus" {
        val mapVector = mapVector("a" to 1.0, "b" to 2.0)
        val result = -mapVector
        result shouldBe mapVector("a" to -1.0, "b" to -2.0)
    }
    "times" {
        val mapVector = mapVector("a" to 1.0, "b" to 2.0)
        val result = mapVector * 2.0
        result shouldBe mapVector("a" to 2.0, "b" to 4.0)
    }
    "times 0" {
        val mapVector = mapVector("a" to 1.0, "b" to 2.0)
        val result = mapVector * 0.0
        result shouldBe MapVector.zero()
    }
    "div" {
        val mapVector = mapVector("a" to 1.0, "b" to 2.0)
        val result = mapVector / 2.0
        result shouldBe mapVector("a" to 0.5, "b" to 1.0)
    }
    "div infinite" {
        val mapVector = mapVector("a" to 1.0, "b" to 2.0)
        mapVector / Double.POSITIVE_INFINITY shouldBe MapVector.zero()
        mapVector / Double.NEGATIVE_INFINITY shouldBe MapVector.zero()
    }
    "closeTo" {
        val a = mapVector("a" to 1.0, "b" to 2.0)
        val b = mapVector("a" to 1.1, "b" to 2.1, "c" to 0.1)
        a.closeTo(b, 0.11) shouldBe true
        a.closeTo(b, 0.05) shouldBe false
    }
})
