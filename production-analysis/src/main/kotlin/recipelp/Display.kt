package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Process
import glassbricks.recipeanalysis.Symbol
import kotlin.math.absoluteValue

interface RecipeLpFormatter {
    fun formatInput(input: Input): String = formatIngredient(input.ingredient)
    fun formatOutput(output: Output): String = formatIngredient(output.ingredient)
    fun formatProcess(process: LpProcess): String = formatOtherProcess(process)
    fun formatOtherProcess(process: PseudoProcess): String = process.toString()
    fun formatAnyPseudoProcess(process: PseudoProcess): String = when (process) {
        is Input -> formatInput(process)
        is Output -> formatOutput(process)
        is LpProcess -> formatProcess(process)
        else -> formatOtherProcess(process)
    }

    fun formatRate(rate: Double): String = "%.5f".format(rate) + "/s"
    fun formatInputOutputRate(rate: Double): String = formatRate(rate)
    fun formatMachineUsage(usage: Double): String = "%.5f".format(usage)
    fun formatSymbolUsage(usage: Double): String = "%.5f".format(usage)
    fun formatThroughput(throughput: Throughput): String =
        if (throughput.net.absoluteValue < 1e-6) formatRate(throughput.min)
        else formatRate(throughput.min) + " (${formatRate(throughput.net)})"

    fun formatSurplusIngredient(ingredient: Ingredient): String = formatIngredient(ingredient)
    fun formatIngredient(ingredient: Ingredient): String = formatSymbol(ingredient)
    fun formatSymbol(symbol: Symbol): String = symbol.toString()

    val inputComparator: Comparator<in Input>? get() = null
    val outputComparator: Comparator<in Output>? get() = null
    val lpProcessComparator: Comparator<in LpProcess>? get() = processComparator?.let { compareBy(it) { it.process } }
    val otherProcessComparator: Comparator<in PseudoProcess>? get() = null
    val processComparator: Comparator<in Process>? get() = null

    val ingredientComparator: Comparator<in Ingredient>? get() = symbolComparator
    val symbolComparator: Comparator<in Symbol>? get() = compareBy { it.toString() }

    companion object Default : RecipeLpFormatter
}

private fun <T> List<T>.maybeSort(comparator: Comparator<in T>?) =
    if (comparator != null) sortedWith(comparator) else this

private inline fun <T> StringBuilder.displayLeftRight(
    list: List<T>,
    left: (T) -> String,
    right: (T) -> String,
    separator: String = " | ",
) = apply {
    val lefts = list.map { left(it) }
    val leftWidth = lefts.maxOfOrNull { it.length } ?: 0
    for ((el, left) in list.zip(lefts)) {
        append(left)
        repeat(leftWidth - left.length) { append(' ') }
        append(separator)
        appendLine(right(el))
    }
}

fun RecipeLpSolution.textDisplay(formatter: RecipeLpFormatter = RecipeLpFormatter): String = buildString {
    appendLine("Inputs:")
    val inputs = processUsage.keys.filterIsInstance<Input>().maybeSort(formatter.inputComparator)
    displayLeftRight(inputs, formatter::formatInput, { formatter.formatInputOutputRate(processUsage[it]) })
    appendLine()

    appendLine("Outputs:")
    val outputs = processUsage.keys.filterIsInstance<Output>().maybeSort(formatter.outputComparator)
    displayLeftRight(outputs, formatter::formatOutput, { formatter.formatInputOutputRate(processUsage[it]) })
    appendLine()

    appendLine("Recipes:")
    val processes = processUsage.keys.filterIsInstance<LpProcess>().maybeSort(formatter.lpProcessComparator)
    displayLeftRight(processes, formatter::formatProcess, { formatter.formatMachineUsage(processUsage[it]) })
    appendLine()

    val otherProcesses = processUsage.keys.filter {
        it !is Input && it !is Output && it !is LpProcess
    }.maybeSort(formatter.otherProcessComparator)
    if (otherProcesses.isNotEmpty()) {
        appendLine()
        appendLine("Other processes:")
        displayLeftRight(
            otherProcesses,
            formatter::formatOtherProcess,
            { formatter.formatMachineUsage(processUsage[it]) })
    }
    val throughputs = throughputs
    if (throughputs.isNotEmpty()) {
        appendLine()
        appendLine("Throughputs:")
        displayLeftRight(
            throughputs.keys.toList().maybeSort(formatter.ingredientComparator),
            formatter::formatIngredient,
            { formatter.formatThroughput(throughputs[it]!!) })
    }

    if (surpluses.isNotEmpty()) {
        appendLine()
        appendLine("Surpluses:")
        displayLeftRight(
            surpluses.keys.toList().maybeSort(formatter.ingredientComparator),
            formatter::formatSurplusIngredient,
            { formatter.formatRate(surpluses[it]) })
    }

    if (symbolUsage.isNotEmpty()) {
        appendLine()
        appendLine("Symbols used:")
        displayLeftRight(
            symbolUsage.keys.toList().maybeSort(formatter.symbolComparator),
            formatter::formatSymbol,
            { formatter.formatSymbolUsage(symbolUsage[it]) })
    }
}

fun RecipeLpSolution.dotDisplay(formatter: RecipeLpFormatter = RecipeLpFormatter): String = buildString {
    appendLine("digraph {")
    appendLine("  overlap=false")
    appendLine("  layout=dot")
    // node for every ingredient throughput
    for ((ingredient, throughput) in throughputs) {
        val ingredientStr = formatter.formatIngredient(ingredient)
        val throughputStr = formatter.formatThroughput(throughput)
        val name = ingredient.toString()
        appendLine("  \"$name\" [label=\"$ingredientStr\\n$throughputStr\"]")
    }
    for ((process, amount) in processUsage) {
        val processStr = formatter.formatAnyPseudoProcess(process)
        val amountStr = formatter.formatMachineUsage(amount)
        val name = process.toString()
        appendLine("  \"$name\" [label=\"$processStr\\n$amountStr\"]")
    }
}
