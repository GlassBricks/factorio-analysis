package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.BeaconPrototype
import glassbricks.factorio.prototypes.ItemPrototype
import glassbricks.factorio.prototypes.ModulePrototype
import glassbricks.factorio.recipes.problem.Solution
import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.recipelp.*
import java.awt.Color

private fun String.replacePrefix(prefix: String, replacement: String): String =
    if (startsWith(prefix)) replacement + substring(prefix.length) else this

fun moduleShorthandName(prototype: ModulePrototype): String =
    prototype.name
        .replacePrefix("productivity", "prod")
        .replacePrefix("effectivity", "eff")
        .replacePrefix("quality", "qual")
        .replace("-module", "")
        .replace(Regex("-(\\d+)$"), "$1")

interface FactorioShorthandFormatter : FactorioRecipesFormatter {
    override fun formatItemName(prototype: ItemPrototype): String = when (prototype) {
        is ModulePrototype -> moduleShorthandName(prototype)
        else -> super.formatItemName(prototype)
    }

    override fun formatBeaconName(prototype: BeaconPrototype): String {
        if (prototype.name == "beacon") return "b"
        return super.formatBeaconName(prototype)
    }
}

interface FactorioGraphExportFormatter : FactorioShorthandFormatter {
    override fun formatSetup(setup: MachineSetup<*>): String =
        formatProcess(setup.process) + "\n" + formatMachine(setup.machine)

    companion object Default : FactorioGraphExportFormatter
}

fun Color.toHex(): String = String.format("#%02x%02x%02x", red, green, blue)

data class DotGraphExportOptions(
    val recipeNodeAttributes: Map<String, Any> = emptyMap(),
    val ingredientNodeAttributes: Map<String, Any> = emptyMap(),
    val inputEdgeAttributes: Map<String, Any> = emptyMap(),
    val outputEdgeAttributes: Map<String, Any> = emptyMap(),
    val graphAttributes: Map<String, Any> = emptyMap(),
) {
    companion object {
        val default = DotGraphExportOptions(
            // light gray filled box
            recipeNodeAttributes = mapOf(
                "shape" to "box",
                "style" to "filled",
                "fillcolor" to "#eeeeee",
            ),
            ingredientNodeAttributes = mapOf(
                "shape" to "box",
                "color" to "#006400",
            ),
            inputEdgeAttributes = mapOf(
//                "decorate" to true,
                "color" to Color.getHSBColor(0.1f, 0.9f, 0.6f).toHex(),
            ),
            outputEdgeAttributes = mapOf(
//                "decorate" to true,
                "color" to Color.getHSBColor(0.6f, 0.9f, 0.6f).toHex(),
            ),
            graphAttributes = mapOf(
                "layout" to "dot",
//                "concentrate" to true,
                "compound" to true,
                "ranksep" to 1.0,
            ),
        )
    }
}

data class ThroughputDotGraphExport(
    val dotGraph: DotGraph,
    val nodeMap: Map<ThroughputGraphNode, DotGraphNode>,
    val edgeMap: Map<EdgeInfo<ThroughputGraphNode, Double>, DotGraphEdge>,
    val solution: Solution,
    val formatter: FactorioRecipesFormatter,
) : WithFactorioPrototypes {
    override val prototypes get() = solution.prototypes
    val reverseNodeMap = nodeMap.entries.associate { (k, v) -> v.id to k }
}

fun Solution.toFancyDotGraph(
    formatter: FactorioRecipesFormatter = FactorioGraphExportFormatter,
    exportOptions: DotGraphExportOptions = DotGraphExportOptions.default,
    modify: ThroughputDotGraphExport.() -> Unit = {},
): DotGraph {
    val export = recipeSolution.toThroughputGraph().toDotGraph(
        nodeAttributes = { node ->
            val label = when (node) {
                is RecipeNode -> formatter.formatAnyPseudoProcess(node.process) + "\n" +
                        formatter.formatMachineUsage(processUsage[node.process])

                is ThroughputNode -> formatter.formatSymbol(node.ingredient) + "\n" +
                        formatter.formatThroughput(throughputs[node.ingredient]!!)
            }
            when (node) {
                is RecipeNode -> exportOptions.recipeNodeAttributes
                is ThroughputNode -> exportOptions.ingredientNodeAttributes
            }
                .plus("label" to label)
        },
        edgeAttributes = { (from, _, weight) ->
            val isInput = from is ThroughputNode
            val label = formatter.formatRate(weight)

            if (isInput) {
                exportOptions.inputEdgeAttributes
            } else {
                exportOptions.outputEdgeAttributes
            }
//                .plus("label" to label)
        },
        graphAttributes = exportOptions.graphAttributes,
    )
    val throughputDotGraphExport = ThroughputDotGraphExport(
        dotGraph = export.dotGraph,
        nodeMap = export.nodeMap,
        edgeMap = export.edgeMap,
        solution = this,
        formatter = formatter,
    )
    modify(throughputDotGraphExport)
    return export.dotGraph
}

fun ThroughputDotGraphExport.clusterItemsByQuality() {
    val itemsByPrototype = nodeMap.keys
        .mapNotNull { (it as? ThroughputNode)?.ingredient as? Item }
        .groupBy { it.prototype }
        .filterValues { it.size > 1 }
        .mapValues { it.value.sortedBy { it.quality.level } }
    val nodeToSubgraph = mutableMapOf<String, String>()
    for ((prototype, items) in itemsByPrototype) {
        val clusterName = "cluster_" + prototype.name.replace("-", "_")
        val subgraph = DotGraph(type = "subgraph", name = clusterName)
        for (item in items) {
            val node = nodeMap[ThroughputNode(item)]!!
            nodeToSubgraph[node.id] = clusterName
            subgraph.nodes[node.id] = DotGraphNode(
                id = node.id,
                attributes = mutableMapOf(
                    "label" to formatter.formatQualityName(item.quality.prototype) +
                            "\\n" + formatter.formatThroughput(
                        solution.recipeSolution.throughputs[item]!!
                    ),
                )
            )
        }
        subgraph.attributes += mapOf(
            "label" to formatter.formatItemName(prototype),
            "labeljust" to attr("l"),
        )
        dotGraph.subgraphs.add(subgraph)
    }
    // point edges to subgraph instead
    for (edge in dotGraph.edges) {
        dotGraph.nodes[edge.from]?.id?.let { nodeToSubgraph[it] }
            ?.let { edge.attributes["ltail"] = it }
        dotGraph.nodes[edge.to]?.id?.let { nodeToSubgraph[it] }
            ?.let { edge.attributes["lhead"] = it }
    }
}

private fun <K, V> MutableMap<K, V>.putOrRemove(key: K, value: V?) {
    if (value == null) {
        remove(key)
    } else {
        put(key, value)
    }
}

fun ThroughputDotGraphExport.unconstrainEdgesForMachine(machine: CraftingMachine) {
    val processes = nodeMap.filter { (node) ->
        (node as? RecipeNode)?.let { recipeNode ->
            (recipeNode.process as? LpProcess)?.let { lpProcess ->
                (lpProcess.process as? MachineSetup<*>)?.machine?.prototype
            }
        } == machine.prototype
    }
    val processIds = processes.values.map { it.id }.toSet()
    for (edge in dotGraph.edges) {
        if (edge.from in processIds || edge.to in processIds) {
            edge.from = edge.to.also { edge.to = edge.from }
            edge.attributes["dir"] = attr("back")
            val tail = edge.attributes["ltail"]
            val head = edge.attributes["lhead"]
            edge.attributes.putOrRemove("ltail", head)
            edge.attributes.putOrRemove("lhead", tail)
        }
    }
}
