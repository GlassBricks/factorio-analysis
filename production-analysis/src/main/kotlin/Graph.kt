package glassbricks.recipeanalysis

import java.io.File

// Own implementation of graph instead of jgrapht

interface Graph<N, E> {
    val nodes: Collection<N>

    fun getEdge(from: N, to: N): E?
    fun getDirectedEdges(from: N): Collection<EdgeInfo<N, E>>
    fun getDirectedReverseEdges(to: N): Collection<EdgeInfo<N, E>>
}

interface MutableGraph<N, E> : Graph<N, E> {
    fun addNode(node: N)

    /** Returns true if the edge was added, false if it already existed. */
    fun addEdge(from: N, to: N, edge: E): Boolean

    fun removeNode(node: N): Boolean
    fun removeEdge(from: N, to: N): E?
}

data class EdgeInfo<N, E>(
    val fromNode: N,
    val toNode: N,
    val edge: E,
)

/**
 * Directed graph, up to one edge in each direction between nodes, allows self loops.
 */
class DirectedHashGraph<N, E> : MutableGraph<N, E> {
    private val edges = mutableMapOf<N, MutableMap<N, EdgeInfo<N, E>>>()
    private val reverseEdges = mutableMapOf<N, MutableMap<N, EdgeInfo<N, E>>>()

    override val nodes: Collection<N> get() = edges.keys

    override fun getEdge(from: N, to: N): E? = edges[from]?.get(to)?.edge
    override fun getDirectedEdges(from: N): Collection<EdgeInfo<N, E>> = edges[from]?.values ?: emptyList()
    override fun getDirectedReverseEdges(to: N): Collection<EdgeInfo<N, E>> = reverseEdges[to]?.values ?: emptyList()
    override fun addNode(node: N) {
        edges.computeIfAbsent(node) { mutableMapOf() }
        reverseEdges.computeIfAbsent(node) { mutableMapOf() }
    }

    override fun addEdge(from: N, to: N, edge: E): Boolean {
        require(from in nodes && to in nodes) { "Both nodes must be in the graph" }
        val fromEdges = edges[from]!!
        if (to in fromEdges) return false
        fromEdges[to] = EdgeInfo(from, to, edge)
        reverseEdges[to]!![from] = EdgeInfo(from, to, edge)
        return true
    }

    override fun removeNode(node: N): Boolean {
        if (node !in nodes) return false
        for (toNode in edges[node]!!.keys) {
            reverseEdges[toNode]?.remove(node)
        }
        for (fromNode in reverseEdges[node]!!.keys) {
            edges[fromNode]?.remove(node)
        }
        edges.remove(node)
        reverseEdges.remove(node)
        return true
    }

    override fun removeEdge(from: N, to: N): E? {
        val edge = edges[from]?.remove(to) ?: return null
        reverseEdges[to]?.remove(from)
        return edge.edge
    }
}

class DotGraphNode(
    val id: String,
    val attributes: MutableMap<String, Any> = mutableMapOf(),
) {
    fun export(): String = if (attributes.isNotEmpty()) "$id [${attributes.exportAttributes()}]" else id
}

data class LiteralAttr(val value: String) {
    override fun toString(): String = value
}

fun attr(value: String): LiteralAttr = LiteralAttr(value)

class DotGraphEdge(
    var from: String,
    var to: String,
    val attributes: MutableMap<String, Any> = mutableMapOf(),
) {
    fun export(): String = if (attributes.isNotEmpty()) {
        "$from -> $to [${attributes.exportAttributes()}]"
    } else
        "$from -> $to"
}

fun String.escape(): String = "\"${replace("\"", "\\\"")}\""
private fun exportAttributeValue(value: Any): String = if (value is String) value.escape() else value.toString()
private fun MutableMap<String, Any>.exportAttributes(): String = entries.joinToString("; ") { (key, value) ->
    "${key}=${exportAttributeValue(value)}"
}

class DotGraph(
    var type: String = "digraph",
    var name: String = "",
    val nodes: MutableMap<String, DotGraphNode> = mutableMapOf(),
    val edges: MutableSet<DotGraphEdge> = mutableSetOf(),
    val attributes: MutableMap<String, Any> = mutableMapOf(),
    val subgraphs: MutableList<DotGraph> = mutableListOf(),
) {
    fun export(appendable: Appendable): Appendable = appendable.apply {
        appendLine("$type $name {")
        for ((key, value) in attributes) {
            appendLine("  $key=${exportAttributeValue(value)}")
        }
        for ((_, node) in nodes) {
            appendLine("  ${node.export()}")
        }
        for (edge in edges) {
            appendLine("  ${edge.export()}")
        }
        for (subgraph in subgraphs) {
            subgraph.export(appendable)
        }
        appendLine("}")
    }
}

fun DotGraph.writeTo(file: File) {
    file.printWriter().use { export(it) }
}

fun <N> idProvider(): (N) -> String {
    var id = 0
    return { id++.toString() }
}

data class DotGraphExport<N, E>(
    val dotGraph: DotGraph,
    val nodeMap: Map<N, DotGraphNode>,
    val edgeMap: Map<EdgeInfo<N, E>, DotGraphEdge>,
)

fun <N, E> Graph<N, E>.toDotGraph(
    idProvider: (N) -> String = idProvider(),
    nodeAttributes: (N) -> Map<String, Any>? = { null },
    edgeAttributes: (EdgeInfo<N, E>) -> Map<String, Any>? = { null },
    graphAttributes: Map<String, Any> = emptyMap(),
): DotGraphExport<N, E> {
    val dotGraph = DotGraph()
    dotGraph.attributes.putAll(graphAttributes)
    val nodeMap = mutableMapOf<N, DotGraphNode>()
    for (node in this.nodes) {
        val id = idProvider(node)
        val dotNode = DotGraphNode(id, nodeAttributes(node)?.toMutableMap() ?: mutableMapOf())
        dotGraph.nodes[id] = dotNode
        nodeMap[node] = dotNode
    }
    val edgeMap = mutableMapOf<EdgeInfo<N, E>, DotGraphEdge>()
    for (fromNode in this.nodes) {
        for (edgeInfo in this.getDirectedEdges(fromNode)) {
            val fromId = nodeMap[edgeInfo.fromNode]!!.id
            val toId = nodeMap[edgeInfo.toNode]!!.id
            val edge = DotGraphEdge(fromId, toId, edgeAttributes(edgeInfo)?.toMutableMap() ?: mutableMapOf())
            dotGraph.edges.add(edge)
            edgeMap[edgeInfo] = edge
        }
    }
    return DotGraphExport(dotGraph, nodeMap, edgeMap)
}
