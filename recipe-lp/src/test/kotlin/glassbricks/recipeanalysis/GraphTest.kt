package glassbricks.recipeanalysis

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.properties.ReadOnlyProperty

fun <T : Any> TestConfiguration.createEach(
    create: () -> T,
): ReadOnlyProperty<Any?, T> {
    var value: T? = null
    beforeEach {
        value = create()
    }
    afterEach {
        value = null
    }
    return ReadOnlyProperty { _, _ -> value ?: error("Value accessed before initialization") }
}

class HashGraphTest : StringSpec({
    val graph by createEach { HashDirectedGraph<Int, Int>() }

    "addNode" {
        graph.nodes shouldBe emptySet()
        graph.addNode(2)
        graph.addNode(3)
        graph.nodes shouldBe setOf(2, 3)

        graph.addNode(2) shouldBe false

        graph.removeNode(2) shouldBe true
        graph.nodes shouldBe setOf(3)
        graph.removeNode(2) shouldBe false

        graph.removeNode(3) shouldBe true
        graph.nodes shouldBe emptySet()
    }

    fun reverseShouldBeEmpty() {
        // reverse should always be empty
        graph.edgesTo(2) should beEmpty()
        graph.edgesFrom(3) should beEmpty()
        graph.edgesTo(2) should beEmpty()
        graph.edgesFrom(3) should beEmpty()
    }

    fun shouldBeEmpty() {
        graph.outNeighbors(2) shouldBe emptySet()
        graph.inNeighbors(3) shouldBe emptySet()
        graph.edgesFrom(2) should beEmpty()
        graph.edgesTo(3) should beEmpty()

        reverseShouldBeEmpty()
    }

    "addEdge" {
        graph.addNode(2)
        graph.addNode(3)

        graph.edge(2, 3) shouldBe null

        shouldBeEmpty()

        graph.addEdge(2, 3, 5) shouldBe true

        graph.edge(2, 3) shouldBe 5

        graph.edgesFrom(2).single() shouldBe MapEntry(3, 5)
        graph.edgesTo(3).single() shouldBe MapEntry(2, 5)
        graph.edgesFrom(3) should beEmpty()
        graph.edgesTo(2) should beEmpty()

        reverseShouldBeEmpty()

        graph.addEdge(2, 3, 5) shouldBe false

        graph.removeEdge(2, 3) shouldBe 5

        graph.nodes shouldBe setOf(2, 3)
    }
})

class MultiHashGraphTest : StringSpec({
    val graph by createEach { HashMultiGraph<Int, Int>() }

    "addNode" {
        graph.nodes shouldBe emptySet()
        graph.addNode(2)
        graph.addNode(3)
        graph.nodes shouldBe setOf(2, 3)

        graph.addNode(2) shouldBe false

        graph.removeNode(2) shouldBe true
        graph.nodes shouldBe setOf(3)
        graph.removeNode(2) shouldBe false

        graph.removeNode(3) shouldBe true
        graph.nodes shouldBe emptySet()
    }

    fun reverseShouldBeEmpty() {
        // reverse should always be empty
        graph.edgesTo(2).toList() should beEmpty()
        graph.edgesFrom(3).toList() should beEmpty()
        graph.edgesTo(2).toList() should beEmpty()
        graph.edgesFrom(3).toList() should beEmpty()
    }

    fun shouldBeEmpty() {
        graph.outNeighbors(2) shouldBe emptySet()
        graph.inNeighbors(3) shouldBe emptySet()
        graph.edgesFrom(2).toList() should beEmpty()
        graph.edgesTo(3).toList() should beEmpty()

        reverseShouldBeEmpty()
    }
    "addEdge" {
        graph.addNode(2)
        graph.addNode(3)

        graph.edge(2, 3) shouldBe null
        shouldBeEmpty()

        graph.addEdge(2, 3, 5) shouldBe true

        graph.edge(2, 3) shouldBe 5
        graph.edges(2, 3).toList() shouldBe listOf(5)

        graph.edgesFrom(2).single() shouldBe MapEntry(3, 5)
        graph.edgesTo(3).single() shouldBe MapEntry(2, 5)
        graph.edgesFrom(3).toList() should beEmpty()
        graph.edgesTo(2).toList() should beEmpty()

        reverseShouldBeEmpty()

        graph.addEdge(2, 3, 6) shouldBe true
        graph.edges(2, 3).toList() shouldBe listOf(5, 6)

        graph.edgesFrom(2).toList() shouldBe listOf(MapEntry(3, 5), MapEntry(3, 6))
        graph.edgesTo(3).toList() shouldBe listOf(MapEntry(2, 5), MapEntry(2, 6))
        graph.edgesFrom(3).toList() should beEmpty()
        graph.edgesTo(2).toList() should beEmpty()

        reverseShouldBeEmpty()
    }
})
