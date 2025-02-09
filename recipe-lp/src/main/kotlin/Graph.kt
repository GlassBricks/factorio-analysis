package glassbricks.recipeanalysis

// Own implementation of graph instead of jgrapht

typealias DirectedEdgeInfo<N, E> = Map.Entry<N, E>

internal data class MapEntry<N, E>(override val key: N, override val value: E) : Map.Entry<N, E> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Map.Entry<*, *>) return false
        return key == other.key && value == other.value
    }

    override fun hashCode(): Int {
        var result = key?.hashCode() ?: 0
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }
}

interface Graph<N, E> {
    val nodes: Collection<N>

    fun outNeighbors(node: N): Collection<N>
    fun inNeighbors(node: N): Collection<N>

    /** If is a multi-graph, returns the first edge found. */
    fun edge(from: N, to: N): E?
    fun edgesFrom(from: N): Iterable<DirectedEdgeInfo<N, E>>
    fun edgesTo(to: N): Iterable<DirectedEdgeInfo<N, E>>
}

interface MultiGraph<N, E> : Graph<N, E> {
    fun edges(from: N, to: N): Collection<E>
}

interface SimpleGraph<N, E> : Graph<N, E>

interface MutableGraph<N, E> : Graph<N, E> {
    fun addNode(node: N): Boolean

    /** Returns true if the edge was added, false if it already existed. */
    fun addEdge(from: N, to: N, edge: E): Boolean

    fun removeNode(node: N): Boolean

    /**
     * If is multi graph, removes any one edge.
     */
    fun removeEdge(from: N, to: N): E?
}

interface MutableSimpleGraph<N, E> : MutableGraph<N, E>, SimpleGraph<N, E>

interface MutableMultiGraph<N, E> : MutableGraph<N, E>, MultiGraph<N, E> {
    fun removeEdge(from: N, to: N, edge: E): Boolean
}

private fun <N, E> removeNodeFromEdges(
    edges: MutableMap<N, MutableMap<N, E>>,
    reverseEdges: MutableMap<N, MutableMap<N, E>>,
    node: N,
): Boolean {
    if (node !in edges) return false
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

class HashDirectedGraph<N, E> : MutableSimpleGraph<N, E> {
    private val edges = mutableMapOf<N, MutableMap<N, E>>()
    private val reverseEdges = mutableMapOf<N, MutableMap<N, E>>()

    override val nodes: Collection<N> get() = edges.keys

    override fun outNeighbors(node: N): Collection<N> = edges[node]?.keys.orEmpty()
    override fun inNeighbors(node: N): Collection<N> = reverseEdges[node]?.keys.orEmpty()
    override fun edge(from: N, to: N): E? = edges[from]?.get(to)
    override fun edgesFrom(from: N): Collection<DirectedEdgeInfo<N, E>> = edges[from]?.entries.orEmpty()
    override fun edgesTo(to: N): Collection<DirectedEdgeInfo<N, E>> =
        reverseEdges[to]?.entries.orEmpty()

    override fun addNode(node: N): Boolean {
        if (node in nodes) return false
        edges[node] = mutableMapOf()
        reverseEdges[node] = mutableMapOf()
        return true
    }

    override fun addEdge(from: N, to: N, edge: E): Boolean {
        require(from in nodes) { "Node not in graph: $from" }
        require(to in nodes) { "Node not in graph: $to" }
        val fromEdges = edges[from]!!
        if (to in fromEdges) return false
        fromEdges[to] = edge
        reverseEdges[to]!![from] = edge
        return true
    }

    override fun removeNode(node: N): Boolean = removeNodeFromEdges(edges, reverseEdges, node)

    override fun removeEdge(from: N, to: N): E? {
        val edge = edges[from]?.remove(to) ?: return null
        reverseEdges[to]?.remove(from)
        return edge
    }
}

fun <N> MutableGraph<N, Unit>.addEdge(from: N, to: N): Boolean = addEdge(from, to, Unit)

class HashMultiGraph<N, E> : MutableMultiGraph<N, E> {
    private val edges = mutableMapOf<N, MutableMap<N, MutableList<E>>>()
    private val reverseEdges = mutableMapOf<N, MutableMap<N, MutableList<E>>>()

    override val nodes: Collection<N> get() = edges.keys

    override fun outNeighbors(node: N): Collection<N> = edges[node]?.keys.orEmpty()
    override fun inNeighbors(node: N): Collection<N> = reverseEdges[node]?.keys.orEmpty()
    override fun edge(from: N, to: N): E? = edges[from]?.get(to)?.firstOrNull()
    override fun edges(from: N, to: N): Collection<E> = edges[from]?.get(to).orEmpty()
    override fun edgesFrom(from: N): Iterable<DirectedEdgeInfo<N, E>> =
        edges[from]?.asSequence().orEmpty()
            .flatMap { (to, edges) -> edges.asSequence().map { MapEntry(to, it) } }
            .asIterable()

    override fun edgesTo(to: N): Iterable<DirectedEdgeInfo<N, E>> =
        reverseEdges[to]?.asSequence().orEmpty()
            .flatMap { (from, edges) -> edges.asSequence().map { MapEntry(from, it) } }
            .asIterable()

    override fun addNode(node: N): Boolean {
        if (node in nodes) return false
        edges[node] = mutableMapOf()
        reverseEdges[node] = mutableMapOf()
        return true
    }

    override fun addEdge(from: N, to: N, edge: E): Boolean {
        require(from in nodes && to in nodes) { "Both nodes must be in the graph" }
        val fromEdges = edges[from]!!
        val toEdges = reverseEdges[to]!!
        fromEdges.computeIfAbsent(to) { mutableListOf() }.add(edge)
        toEdges.computeIfAbsent(from) { mutableListOf() }.add(edge)
        return true
    }

    override fun removeNode(node: N): Boolean = removeNodeFromEdges(edges, reverseEdges, node)

    override fun removeEdge(from: N, to: N): E? {
        val fromEdges = edges[from] ?: return null
        val toEdges = reverseEdges[to] ?: return null
        val removedEdge = fromEdges[to]?.removeLast() ?: return null
        toEdges[from]?.remove(removedEdge)
        return removedEdge
    }

    override fun removeEdge(from: N, to: N, edge: E): Boolean {
        val fromEdges = edges[from] ?: return false
        val toEdges = reverseEdges[to] ?: return false
        val removedFrom = fromEdges[to]?.remove(edge) == true
        val removedTo = toEdges[from]?.remove(edge) == true
        return removedFrom || removedTo
    }
}

fun <N, E> MutableGraph<N, E>.copyEdges(sourceNode: N, destNode: N) {
    for ((toNode, edge) in edgesFrom(sourceNode)) {
        addEdge(destNode, toNode, edge)
    }
    for ((fromNode, edge) in edgesTo(sourceNode)) {
        addEdge(fromNode, destNode, edge)
    }
}

fun <N, E> MutableGraph<N, E>.removeAllNodes(nodes: Iterable<N>) {
    for (node in nodes) removeNode(node)
}
