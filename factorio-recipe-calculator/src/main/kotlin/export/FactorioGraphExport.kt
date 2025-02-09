package glassbricks.factorio.recipes.export

import glassbricks.factorio.prototypes.*
import glassbricks.factorio.recipes.Fluid
import glassbricks.factorio.recipes.Item
import glassbricks.factorio.recipes.MachineSetup
import glassbricks.factorio.recipes.problem.machine
import glassbricks.factorio.recipes.problem.recipe
import glassbricks.recipeanalysis.DotGraph
import glassbricks.recipeanalysis.DotGraphExport
import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Vector
import glassbricks.recipeanalysis.recipelp.*
import java.awt.Color

val shorthandMap = mapOf(
    "electromagnetic-plant" to "EMP",
    "assembling-machine" to "asm",
    "chemical-plant" to "chem plant",
    "oil-refinery" to "refinery",
    "quality-module" to "qual",
    "productivity-module" to "prod",
    "speed-module" to "speed",
    "efficiency-module" to "eff mod",
    "electronic-circuit" to "GC",
    "advanced-circuit" to "RC",
    "processing-unit" to "BC",
    "low-density-structure" to "LDS",
    "-recycling" to "\\nrecycling",
    "pipe-to-ground" to "UG pipe"
)

fun shorthandDisplayName(name: String): String {
    var name = name
    for ((key, value) in shorthandMap) {
        name = name.replace(key, value)
    }
    return name.replace("-", " ")
        .replaceFirstChar { it.titlecase() }
}

val qualityNamesMap = mapOf(
    "normal" to "Q1",
    "uncommon" to "Q2",
    "rare" to "Q3",
    "epic" to "Q4",
    "legendary" to "Q5",
)

interface FactorioShorthandFormatter : FactorioRecipesFormatter {
    override fun formatItemName(prototype: ItemPrototype): String = shorthandDisplayName(prototype.name)
    override fun formatFluid(fluid: Fluid): String = shorthandDisplayName(fluid.prototype.name)
    override fun formatRecipeName(prototype: RecipePrototype): String = shorthandDisplayName(prototype.name)
    override fun formatResourceName(prototype: ResourceEntityPrototype): String = shorthandDisplayName(prototype.name)
    override fun formatEntityName(prototype: EntityPrototype): String = shorthandDisplayName(prototype.name)

    override fun formatQualityName(prototype: QualityPrototype): String =
        qualityNamesMap[prototype.name] ?: prototype.name

    companion object Default : FactorioShorthandFormatter
}

interface FactorioGraphExportFormatter : FactorioShorthandFormatter {
    override fun formatSetup(setup: MachineSetup<*>): String =
        formatRecipeOrResource(setup.recipe) + "\n" + formatMachine(setup.machine)

    companion object Default : FactorioGraphExportFormatter
}

fun ThroughputGraphBuilder.mergeItemsByQuality() {
    ingredientToNode.keys
        .filterIsInstance<Item>()
        .groupBy { it.prototype }
        .forEach { (_, items) -> mergeIngredients(items) }
}

fun ThroughputGraphBuilder.mergeRecipesByQuality() {
    processToNode.keys
        .groupBy { it.recipe()?.prototype }
        .forEach { (key, recipes) ->
            if (key != null) mergeProcesses(recipes)
        }
}

fun Color.toHex(): String = String.format("#%02x%02x%02x", red, green, blue)

data class DotGraphExportOptions(
    val graphAttributes: Map<String, Any> = emptyMap(),
    val nodeAttributes: Map<String, Any> = emptyMap(),
    val edgeAttributes: Map<String, Any> = emptyMap(),
    val recipeNodeAttributes: Map<String, Any> = emptyMap(),
    val ingredientNodeAttributes: Map<String, Any> = emptyMap(),
    val inputEdgeAttributes: Map<String, Any> = emptyMap(),
    val outputEdgeAttributes: Map<String, Any> = emptyMap(),
) {
    companion object {
        val default = DotGraphExportOptions(
            graphAttributes = mapOf(
                "layout" to "dot",
                "concentrate" to true,
            ),
            nodeAttributes = mapOf(
                "shape" to "box",
                "margin" to 0,
            ),
            edgeAttributes = mapOf(
                "decorate" to true,
            ),
            recipeNodeAttributes = mapOf(
                "style" to "filled",
                "color" to Color.getHSBColor(0.8f, 0.7f, 0.6f).toHex(),
                // light blue
                "fillcolor" to "#cceeff",
            ),
            ingredientNodeAttributes = mapOf(
                "color" to "#006400",
            ),
            inputEdgeAttributes = mapOf(
                "color" to Color.getHSBColor(0.1f, 0.9f, 0.6f).toHex(),
            ),
            outputEdgeAttributes = mapOf(
                "color" to Color.getHSBColor(0.6f, 0.9f, 0.6f).toHex(),
            ),
        )
    }
}

fun ThroughputGraph.toFancyDotGraph(
    formatter: FactorioRecipesFormatter = FactorioGraphExportFormatter,
    exportOptions: DotGraphExportOptions = DotGraphExportOptions.default,
): DotGraph {
    val graphExport = toDotGraphExport(
        destination = DotGraph(
            attributes = exportOptions.graphAttributes,
            nodeAttributes = exportOptions.nodeAttributes,
            edgeAttributes = exportOptions.edgeAttributes,
        ),
        ingredientNodeAttrs = { node ->
            exportOptions.ingredientNodeAttributes + formatIngNode(node.ingredients, formatter)
        },
        processNodeAttrs = { node ->
            exportOptions.recipeNodeAttributes + formatProcessNode(node, formatter)
        },
        inputEdgeAttrs = { from, to ->
            exportOptions.inputEdgeAttributes + formatEdge(
                inputThroughput(from.ingredients, to.processes),
                from,
                formatter,
                true
            )
        },
        outputEdgeAttrs = { from, to ->
            exportOptions.outputEdgeAttributes + formatEdge(
                outputThroughput(from.processes, to.ingredients),
                to,
                formatter,
                false
            )
        },
    )
    addSinkSource(graphExport)
    return graphExport.dotGraph
}

private fun ThroughputGraph.formatIngNode(
    ingredients: Set<Ingredient>,
    formatter: FactorioRecipesFormatter,
): Map<String, Any> = if (ingredients.size == 1) {
    mapOf("label" to formatSingleIngNode(ingredients.single(), formatter))
} else {
    mapOf(
        "label" to formatMultipleIngNode(ingredients, formatter),
        "shape" to "record"
    )
}

private fun ThroughputGraph.formatSingleIngNode(
    ingredient: Ingredient,
    formatter: FactorioRecipesFormatter,
): String {
    return formatter.formatSymbol(ingredient) + "\n" + formatter.formatThroughput(solution.throughputs[ingredient]!!)
}

private fun ThroughputGraph.formatMultipleIngNode(
    ingredients: Set<Ingredient>,
    formatter: FactorioRecipesFormatter,
): String {
    val table = mutableListOf<List<String>>()
    val ingItemsByProto = ingredients.filterIsInstance<Item>().groupBy { it.prototype }
    for ((proto, items) in ingItemsByProto) {
        if (items.size == 1) {
            table += listOf(
                formatter.formatItemName(proto),
                formatter.formatThroughput(solution.throughputs[items.single()]!!)
            )
        } else {
            // header
            table += listOf(formatter.formatItemName(proto))
            for (item in items.sortedBy { it.quality }) {
                table += listOf(
                    formatter.formatQualityName(item.quality.prototype),
                    formatter.formatThroughput(solution.throughputs[item]!!)
                )
            }
        }
    }
    val otherIngredients = ingredients.filter { it !is Item }
    for (ingredient in otherIngredients) {
        table += listOf(
            formatter.formatIngredient(ingredient),
            formatter.formatThroughput(solution.throughputs[ingredient]!!)
        )
    }
    return table.toGraphvizRecord()
}

fun ThroughputGraph.formatProcessNode(
    process: ProcessNode,
    formatter: FactorioRecipesFormatter,
): Map<String, Any> = if (process.processes.size == 1) {
    mapOf("label" to formatSingleProcessNode(process.processes.single(), formatter))
} else {
    mapOf(
        "label" to formatMultipleProcessNode(process.processes, formatter),
        "shape" to "record"
    )
}

private fun ThroughputGraph.formatSingleProcessNode(
    process: PseudoProcess,
    formatter: FactorioRecipesFormatter,
): String {
    return formatter.formatAnyPseudoProcess(process) + "\n" +
            formatter.formatProcessUsage(solution.lpProcesses[process])
}

private fun ThroughputGraph.formatMultipleProcessNode(
    processes: Set<PseudoProcess>,
    formatter: FactorioRecipesFormatter,
): String {
    val table = mutableListOf<List<String>>()
    // recipe
    // --
    // quality(sorted) | machine | rate
    val machineSetups = processes.mapNotNull { it.machine() }
    val byRecipe = machineSetups.groupBy { it.recipe.prototype }
    for ((recipe, setups) in byRecipe) {
        table += listOf(formatter.formatResourceOrRecipeName(recipe))
        for (setup in setups.sortedBy { it.recipe.inputQuality }) {
            table += listOf(
                formatter.formatQualityName(setup.recipe.inputQuality.prototype),
                formatter.formatMachine(setup.machine),
                formatter.formatProcessUsage(solution.processes[setup])
            )
        }
    }
    val otherProcesses = processes.filter { it.machine() == null }
    for (process in otherProcesses) {
        table += listOf(
            formatter.formatAnyPseudoProcess(process),
            formatter.formatProcessUsage(solution.lpProcesses[process])
        )
    }

    return table.toGraphvizRecord()
}

private fun List<List<String>>.toGraphvizRecord() = joinToString("|") {
    it.joinToString(prefix = "{", separator = "|", postfix = "}")
}

private fun ThroughputGraph.formatEdge(
    rate: Vector<Ingredient>,
    ingNode: IngredientNode,
    formatter: FactorioRecipesFormatter,
    isInput: Boolean,
): Map<String, Any> {
    return mapOf("label" to formatEdgeLabel(rate, ingNode, formatter, isInput))
}

private fun ThroughputGraph.formatEdgeLabel(
    rate: Vector<Ingredient>,
    ingNode: IngredientNode,
    formatter: FactorioRecipesFormatter,
    isInput: Boolean,
): String {
    val isAllLabel = if (isInput) {
        graph.outNeighbors(ingNode)
    } else {
        graph.inNeighbors(ingNode)
    }.size == 1
    if (isAllLabel) {
        return "(All)"
    }

    val multiplier = if (isInput) -1 else 1
    fun getRateStr(item: Ingredient) = formatter.formatRate(rate[item] * multiplier)

    val needsItemLabel = !ingNode.ingredients.isUnique { (it as? Item)?.prototype }
    return buildString {
        val ingItemsByProto = rate.keys.filterIsInstance<Item>().groupBy { it.prototype }
        for ((proto, items) in ingItemsByProto) {
            if (needsItemLabel) appendLine(formatter.formatItemName(proto))
            for (item in items.sortedBy { it.quality }) {
                if (items.size > 1) {
                    append(formatter.formatQualityName(item.quality.prototype) + "\t")
                }
                appendLine(getRateStr(item))
            }
        }
        val otherKeys = rate.keys.filter { it !is Item }
        for (key in otherKeys) {
            if (needsItemLabel) append(formatter.formatIngredient(key)).append("\t")
            appendLine(getRateStr(key))
        }
    }
}

private inline fun <T, V> Iterable<T>.isUnique(keySelector: (T) -> V): Boolean {
    var seen = false
    var theItem: V? = null
    for (item in this) {
        val key = keySelector(item)
        if (!seen) {
            theItem = key
            seen = true
        } else if (theItem != key) {
            return false
        }
    }
    return true
}

private fun ThroughputGraph.addSinkSource(graphExport: DotGraphExport<ThroughputNode, Unit>) {
    val sources = mutableSetOf<DotGraph.Node>()
    val sinks = mutableSetOf<DotGraph.Node>()

    fun isInputProcess(process: PseudoProcess): Boolean = process.ingredientRate.values.all { it > 0 }
    fun isOutputProcess(process: PseudoProcess): Boolean = process.ingredientRate.values.all { it < 0 }
    for ((throughputNode, node) in graphExport.nodeMap) {
        if (throughputNode is ProcessNode && throughputNode.processes.all { isInputProcess(it) }) {
            sources += node
        }
        if (throughputNode is ProcessNode && throughputNode.processes.all { isOutputProcess(it) }) {
            sinks += node
        }
    }
    graphExport.dotGraph.subGraphs += DotGraph(
        type = "subgraph",
        attributes = mapOf("rank" to "source"),
        nodes = sources.mapTo(mutableListOf()) { DotGraph.Node(it.id) },
    )
    graphExport.dotGraph.subGraphs += DotGraph(
        type = "subgraph",
        attributes = mapOf("rank" to "sink"),
        nodes = sinks.mapTo(mutableListOf()) { DotGraph.Node(it.id) },
    )
}
