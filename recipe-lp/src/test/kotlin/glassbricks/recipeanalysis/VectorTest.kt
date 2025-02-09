package glassbricks.recipeanalysis

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VectorTest : StringSpec({
    "should remove 0 values on construction" {
        val mapVector = vectorOf("a" to 1.0, "b" to 0.0, "c" to 2.0)
        mapVector shouldBe vectorOf("a" to 1.0, "c" to 2.0)
    }
    "get" {
        val mapVector = vectorOf("a" to 1.0, "b" to 2.0)
        mapVector["a"] shouldBe 1.0
        mapVector["b"] shouldBe 2.0
        mapVector["c"] shouldBe 0.0
    }
    "plus" {
        val mapVector1 = vectorOf("a" to 1.0, "b" to 2.0, "z" to 3.0)
        val mapVector2 = vectorOf("b" to 3.0, "c" to 4.0, "z" to -3.0)
        val result = mapVector1 + mapVector2
        result shouldBe vectorOf("a" to 1.0, "b" to 5.0, "c" to 4.0)
    }
    "minus" {
        val mapVector1 = vectorOf("a" to 1.0, "b" to 2.0, "z" to 3.0)
        val mapVector2 = vectorOf("b" to 3.0, "c" to 4.0, "z" to 3.0)
        val result = mapVector1 - mapVector2
        result shouldBe vectorOf("a" to 1.0, "b" to -1.0, "c" to -4.0)
    }
    "unaryMinus" {
        val mapVector = vectorOf("a" to 1.0, "b" to 2.0)
        val result = -mapVector
        result shouldBe vectorOf("a" to -1.0, "b" to -2.0)
    }
    "times" {
        val mapVector = vectorOf("a" to 1.0, "b" to 2.0)
        val result = mapVector * 2.0
        result shouldBe vectorOf("a" to 2.0, "b" to 4.0)
    }
    "times 0" {
        val mapVector = vectorOf("a" to 1.0, "b" to 2.0)
        val result = mapVector * 0.0
        result shouldBe emptyVector()
    }
    "div" {
        val mapVector = vectorOf("a" to 1.0, "b" to 2.0)
        val result = mapVector / 2.0
        result shouldBe vectorOf("a" to 0.5, "b" to 1.0)
    }
    "div infinite" {
        val mapVector = vectorOf("a" to 1.0, "b" to 2.0)
        mapVector / Double.POSITIVE_INFINITY shouldBe emptyVector()
        mapVector / Double.NEGATIVE_INFINITY shouldBe emptyVector()
    }
    "closeTo" {
        val a = vectorOf("a" to 1.0, "b" to 2.0)
        val b = vectorOf("a" to 1.1, "b" to 2.1, "c" to 0.1)
        a.closeTo(b, 0.11) shouldBe true
        a.closeTo(b, 0.05) shouldBe false
    }
    "building a big vector" {
        val vector = buildVector {
            for (i in 0..10000) {
                this["$i"] = i.toDouble()
            }
        }
        vector["9999"] shouldBe 9999.0
        vector["1337"] shouldBe 1337.0
    }
})
