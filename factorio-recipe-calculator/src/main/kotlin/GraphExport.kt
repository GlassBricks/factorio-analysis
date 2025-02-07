package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.BeaconPrototype
import glassbricks.factorio.prototypes.ItemPrototype
import glassbricks.factorio.prototypes.ModulePrototype
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
        formatRecipeOrResource(setup.recipe) + "\n" + formatMachine(setup.machine)

    override fun formatMachineWithModules(machine: MachineWithModules<*>): String {
        return formatBaseMachine(machine.machine) + "\n" + formatModuleSet(machine.moduleSet)
    }

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
            recipeNodeAttributes = mapOf(
                "shape" to "box",
                "style" to "filled",
                "color" to Color.getHSBColor(0.8f, 0.7f, 0.6f).toHex(),
                // light blue
                "fillcolor" to "#cceeff",
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
    val solution: RecipeSolution,
    val formatter: FactorioRecipesFormatter,
    val options: DotGraphExportOptions = DotGraphExportOptions.default,
) {
    val reverseNodeMap = nodeMap.entries.associate { (k, v) -> v.id to k }
}

fun RecipeSolution.toFancyDotGraph(
    formatter: FactorioRecipesFormatter = FactorioGraphExportFormatter,
    exportOptions: DotGraphExportOptions = DotGraphExportOptions.default,
    modify: ThroughputDotGraphExport.() -> Unit = {},
): DotGraph {
    val export = toThroughputGraph().toDotGraph(
        nodeAttributes = { node ->
            val label = when (node) {
                is ProcessNode -> formatter.formatAnyPseudoProcess(node.process) + "\n" +
                        formatter.formatAnyPseudoProcess(node.process)

                is ThroughputNode -> formatter.formatSymbol(node.ingredient) + "\n" +
                        formatter.formatThroughput(throughputs[node.ingredient]!!)
            }
            when (node) {
                is ProcessNode -> exportOptions.recipeNodeAttributes
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
                .plus("label" to label)
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

inline fun <T> DotGraph.createClusters(
    keys: Collection<T>,
    getNodes: (T) -> List<DotGraphNode>,
    clusterName: (T) -> String,
    clusterAttributes: (T) -> Map<String, Any>,
    nodeAttributes: (T, DotGraphNode) -> Map<String, Any>,
) {
    val nodeToSubgraph = mutableMapOf<String, String>()
    for (key in keys) {
        val clusterName = clusterName(key)
        val subgraph = DotGraph(type = "subgraph", name = clusterName)
        for (node in getNodes(key)) {
            nodeToSubgraph[node.id] = clusterName
            subgraph.nodes[node.id] = DotGraphNode(
                id = node.id,
                attributes = nodeAttributes(key, node).toMutableMap()
            )
        }
        subgraph.attributes += clusterAttributes(key)
        this.subgraphs.add(subgraph)
    }
    // set ltail and lhead attributes for edges
    for (edge in this.edges) {
        this.nodes[edge.from]?.id?.let { nodeToSubgraph[it] }
            ?.let { edge.attributes["ltail"] = it }
        this.nodes[edge.to]?.id?.let { nodeToSubgraph[it] }
            ?.let { edge.attributes["lhead"] = it }
    }
}

fun ThroughputDotGraphExport.clusterItemsByQuality() {
    val itemsByPrototype = nodeMap.keys
        .mapNotNull { (it as? ThroughputNode)?.ingredient as? Item }
        .groupBy { it.prototype }
        .filterValues { it.size > 1 }
        .mapValues { it.value.sortedBy { it.quality.level } }
    dotGraph.createClusters(
        itemsByPrototype.keys,
        getNodes = { prototype ->
            itemsByPrototype[prototype]!!.map { nodeMap[ThroughputNode(it)]!! }
        },
        clusterName = { prototype -> "cluster_item_" + prototype.name.replace("-", "_") },
        clusterAttributes = { prototype ->
            mapOf(
                "label" to formatter.formatItemName(prototype),
                "labeljust" to attr("l"),
            )
        },
        nodeAttributes = { _, node ->
            val item = (reverseNodeMap[node.id]!! as ThroughputNode).ingredient as Item
            mutableMapOf(
                "label" to formatter.formatQualityName(item.quality.prototype) +
                        "\\n" + formatter.formatThroughput(
                    solution.throughputs[item]!!
                ),
            )
        }
    )
}

fun ThroughputDotGraphExport.clusterRecipesByQuality() {
    val recipesByPrototype = nodeMap.keys
        // todo: do something about this nesting
        .mapNotNull {
            ((it as? ProcessNode)?.process as? LpProcess)?.let { process ->
                ((process.process as? MachineSetup<*>)?.recipe as? Recipe)?.let { recipe ->
                    process to recipe
                }
            }
        }
        .groupBy { (_, recipe) -> recipe.prototype }
        .filterValues { it.size > 1 }
        .mapValues { it.value.sortedBy { it.second.inputQuality.level } }
    dotGraph.createClusters(
        recipesByPrototype.keys,
        getNodes = { recipe ->
            recipesByPrototype[recipe]!!.map { nodeMap[ProcessNode(it.first)]!! }
        },
        clusterName = { recipe -> "cluster_recipe_" + recipe.name.replace("-", "_") },
        clusterAttributes = { recipe ->
            mapOf(
                "label" to formatter.formatRecipeName(recipe),
                "labeljust" to attr("l"),
                "fill" to "#eeeeff",
            )
        },
        nodeAttributes = { _, node ->
            val lpProcess = (reverseNodeMap[node.id]!! as ProcessNode).process as LpProcess
            val process = lpProcess.process as MachineSetup<*>
            mutableMapOf(
                "label" to (
                        formatter.formatQualityName(process.recipe.inputQuality.prototype) + "\\n" +
                                formatter.formatMachine(process.machine) + "\\n" +
                                formatter.formatMachineUsage(solution.lpProcesses[lpProcess])),
            )
        }
    )
}

fun ThroughputDotGraphExport.flipEdgesForMachine(machine: CraftingMachine) {
    flipEdgesForMachineIf { it.machine.prototype == machine.prototype }
}

private fun <K, V> MutableMap<K, V>.putOrRemove(key: K, value: V?) {
    if (value == null) {
        remove(key)
    } else {
        put(key, value)
    }
}

private fun <K, V : Any> MutableMap<K, V>.swapKeys(key1: K, key2: K) {
    val value1 = this[key1]
    val value2 = this[key2]
    putOrRemove(key1, value2)
    putOrRemove(key2, value1)
}

/**
 * Should be done last
 */
fun ThroughputDotGraphExport.flipEdgesForMachineIf(predicate: (MachineSetup<*>) -> Boolean) {
    val processes = nodeMap.filter { (node) ->
        node is ProcessNode && ((node.process as? LpProcess)?.process as? MachineSetup<*>)?.let { predicate(it) } == true
    }
    val processIds = processes.values.map { it.id }.toSet()
    for (edge in dotGraph.edges) {
        if (edge.from in processIds || edge.to in processIds) {
            edge.from = edge.to.also { edge.to = edge.from }
            edge.attributes["dir"] = attr("back")
            edge.attributes.swapKeys("ltail", "lhead")
//            edge.attributes.swapKeys("headport", "tailport")
        }
    }
}
