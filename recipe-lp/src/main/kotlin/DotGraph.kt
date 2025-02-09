package glassbricks.recipeanalysis

import java.io.File

data class LiteralAttr(val value: String) {
    override fun toString(): String = value
}

fun attr(value: String): LiteralAttr = LiteralAttr(value)

class DotGraph(
    var type: String = "digraph",
    var name: String = "",
    var attributes: Map<String, Any> = mutableMapOf(),
    var nodeAttributes: Map<String, Any> = mutableMapOf(),
    var edgeAttributes: Map<String, Any> = mutableMapOf(),
    val nodes: MutableList<Node> = mutableListOf(),
    val edges: MutableSet<Edge> = mutableSetOf(),
    val subGraphs: MutableSet<DotGraph> = mutableSetOf(),
) {
    class Node(val id: String, val attributes: Map<String, Any> = emptyMap()) {
        fun export(): String = if (attributes.isNotEmpty()) "$id [${attributes.exportAttributes()}]" else id
    }

    class Edge(
        var from: String,
        var to: String,
        var attributes: Map<String, Any> = emptyMap(),
    ) {
        fun export(): String = if (attributes.isNotEmpty()) {
            "$from -> $to [${attributes.exportAttributes()}]"
        } else {
            "$from -> $to"
        }
    }

    fun export(appendable: Appendable, indent: String = ""): Appendable = appendable.apply {
        append(indent, type)
        if (name != "") append(" $name")
        appendLine(" {")
        val newIndent = "$indent    "
        for ((key, value) in attributes) {
            append(newIndent, key, "=", exportAttributeValue(value)).appendLine()
        }
        if (nodeAttributes.isNotEmpty()) {
            append(newIndent, "node [${nodeAttributes.exportAttributes()}]").appendLine()
        }
        if (edgeAttributes.isNotEmpty()) {
            append(newIndent, "edge [${edgeAttributes.exportAttributes()}]").appendLine()
        }
        for (node in nodes) {
            append(newIndent, node.export()).appendLine()
        }
        for (edge in edges) {
            append(newIndent, edge.export()).appendLine()
        }
        for (subgraph in subGraphs) {
            subgraph.export(this, newIndent)
        }
        append(indent).appendLine("}")
    }

    override fun toString(): String = buildString { export(this) }
}

fun String.escape(): String = "\"${replace("\"", "\\\"")}\""
private fun exportAttributeValue(value: Any): String = if (value is String) value.escape() else value.toString()
private fun Map<String, Any>.exportAttributes(): String = entries.joinToString("; ") { (key, value) ->
    "${key}=${exportAttributeValue(value)}"
}

fun File.writeDotGraph(graph: DotGraph) {
    printWriter().use { graph.export(it) }
}

fun <N> idProvider(): (N) -> String {
    var id = 0
    return { id++.toString() }
}

data class DotGraphExport<N, E>(
    val dotGraph: DotGraph,
    val nodeMap: Map<N, DotGraph.Node>,
    val edgeMap: Map<Triple<N, N, E>, DotGraph.Edge>,
)

fun <N, E> Graph<N, E>.toDotGraphExport(
    idProvider: (N) -> String = idProvider(),
    nodeAttributes: (N) -> Map<String, Any>? = { null },
    edgeAttributes: (N, N, E) -> Map<String, Any>? = { _, _, _ -> null },
    destination: DotGraph = DotGraph(),
): DotGraphExport<N, E> {
    val nodeMap = mutableMapOf<N, DotGraph.Node>()
    for (node in this.nodes) {
        val id = idProvider(node)
        val dotNode = DotGraph.Node(id, nodeAttributes(node).orEmpty())
        destination.nodes += dotNode
        nodeMap[node] = dotNode
    }
    val edgeMap = mutableMapOf<Triple<N, N, E>, DotGraph.Edge>()
    for (fromNode in this.nodes) {
        for ((toNode, edgeInfo) in this.edgesFrom(fromNode)) {
            val fromId = nodeMap[fromNode]!!.id
            val toId = nodeMap[toNode]!!.id
            val edgeAttrs = edgeAttributes(fromNode, toNode, edgeInfo).orEmpty()
            val edge = DotGraph.Edge(fromId, toId, edgeAttrs)
            destination.edges += edge
            edgeMap[Triple(fromNode, toNode, edgeInfo)] = edge
        }
    }
    return DotGraphExport(destination, nodeMap, edgeMap)
}
