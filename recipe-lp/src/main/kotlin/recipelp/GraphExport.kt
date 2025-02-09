package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.*

sealed interface ThroughputNode {

}

class IngredientNode(val ingredients: Set<Ingredient>) : ThroughputNode {
    override fun toString(): String = "IngredientNode(${ingredients.joinToString()})"
}

class ProcessNode(val processes: Set<PseudoProcess>) : ThroughputNode {
    override fun toString(): String = "ProcessNode(${processes.joinToString()})"
}

interface ThroughputGraph {
    val solution: RecipeSolution
    val graph: SimpleGraph<ThroughputNode, Unit>
    val ingredientToNode: Map<Ingredient, IngredientNode>
    val processToNode: Map<PseudoProcess, ProcessNode>

    fun inputThroughput(
        fromIngredients: Set<Ingredient>,
        toProcesses: Set<PseudoProcess>,
    ): Vector<Ingredient> = getMultiThroughput(toProcesses, fromIngredients, false)

    fun outputThroughput(
        fromProcesses: Set<PseudoProcess>,
        toIngredients: Set<Ingredient>,
    ): Vector<Ingredient> = getMultiThroughput(fromProcesses, toIngredients, true)

    private fun getMultiThroughput(
        processes: Set<PseudoProcess>,
        ingredients: Set<Ingredient>,
        wantPositive: Boolean = true,
    ): Vector<Ingredient> {
        val result = mutableMapOf<Ingredient, Double>()
        for (process in processes) {
            val processUsage = solution.lpProcesses[process]
            for ((ingredient, rawRate) in process.ingredientRate) {
                val rate = rawRate * processUsage
                if ((rate > 0) == wantPositive && ingredient in ingredients) {
                    result[ingredient] = (result[ingredient] ?: 0.0) + rate
                }
            }
        }
        return vector(result)
    }

}

fun RecipeSolution.toMutableThroughputGraph(): ThroughputGraphBuilder {
    val graph = HashDirectedGraph<ThroughputNode, Unit>()

    val ingredientToNode = throughputs.mapValuesTo(mutableMapOf()) { (ingredient) ->
        IngredientNode(setOf(ingredient)).also { graph.addNode(it) }
    }
    val processToNode = lpProcesses.mapValuesTo(mutableMapOf()) { (process) ->
        ProcessNode(setOf(process)).also { graph.addNode(it) }
    }

    for ((recipe, usage) in lpProcesses) {
        val node = processToNode[recipe]!!
        for ((ingredient, rate) in recipe.ingredientRate * usage) {
            val ingredientNode = ingredientToNode[ingredient]!!
            if (rate < 0) {
                graph.addEdge(ingredientNode, node)
            } else {
                graph.addEdge(node, ingredientNode)
            }
        }
    }
    return ThroughputGraphBuilder(this, graph, ingredientToNode, processToNode)
}

inline fun RecipeSolution.toThroughputGraph(block: ThroughputGraphBuilder.() -> Unit = {}): ThroughputGraph =
    toMutableThroughputGraph().apply(block)

class ThroughputGraphBuilder(
    override val solution: RecipeSolution,
    override val graph: MutableSimpleGraph<ThroughputNode, Unit>,
    override val ingredientToNode: MutableMap<Ingredient, IngredientNode>,
    override val processToNode: MutableMap<PseudoProcess, ProcessNode>,
) : ThroughputGraph {

    fun mergeIngredients(ingredients: List<Ingredient>) {
        mergeNodes(ingredients, ingredientToNode, ::IngredientNode) { it.ingredients }
    }

    fun mergeProcesses(processes: List<PseudoProcess>) {
        mergeNodes(processes, processToNode, ::ProcessNode) { it.processes }
    }

    private inline fun <K, N : ThroughputNode> mergeNodes(
        keys: List<K>,
        nodeMap: MutableMap<K, N>,
        createNode: (Set<K>) -> N,
        getContents: (N) -> Set<K>,
    ) {
        val nodes = keys.map { nodeMap[it]!! }
        if (nodes.size <= 1) return
        val newNode = createNode(nodes.flatMapTo(mutableSetOf()) { getContents(it) })

        graph.addNode(newNode)
        for (node in nodes) {
            graph.copyEdges(node, newNode)
        }
        graph.removeAllNodes(nodes)
        for (key in keys) nodeMap[key] = newNode
    }
}

fun ThroughputGraph.toDotGraphExport(
    ingredientNodeAttrs: ThroughputGraph.(IngredientNode) -> Map<String, Any>?,
    processNodeAttrs: ThroughputGraph.(ProcessNode) -> Map<String, Any>?,
    inputEdgeAttrs: ThroughputGraph.(IngredientNode, ProcessNode) -> Map<String, Any>?,
    outputEdgeAttrs: ThroughputGraph.(ProcessNode, IngredientNode) -> Map<String, Any>?,
    destination: DotGraph = DotGraph(),
): DotGraphExport<ThroughputNode, Unit> = graph.toDotGraphExport(
    destination = destination,
    nodeAttributes = { node ->
        when (node) {
            is IngredientNode -> ingredientNodeAttrs(this, node)
            is ProcessNode -> processNodeAttrs(node)
        }
    },
    edgeAttributes = { from, to, _ ->
        when {
            from is IngredientNode && to is ProcessNode -> inputEdgeAttrs(this, from, to)
            from is ProcessNode && to is IngredientNode -> outputEdgeAttrs(this, from, to)
            else -> null
        }
    }
)
