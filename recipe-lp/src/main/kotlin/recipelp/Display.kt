package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.Ingredient
import glassbricks.recipeanalysis.Process
import glassbricks.recipeanalysis.Symbol
import kotlin.math.absoluteValue

interface RecipeLpFormatter {
    fun formatInput(input: Ingredient): String = formatIngredient(input)
    fun formatOutput(output: Ingredient): String = formatIngredient(output)
    fun formatProcess(process: Process): String = process.toString()
    fun formatOtherProcess(process: PseudoProcess): String = process.toString()
    fun formatAnyPseudoProcess(process: PseudoProcess): String = when (process) {
        is Input -> formatInput(process.ingredient)
        is Output -> formatOutput(process.ingredient)
        is RealProcess -> formatProcess(process.process)
        else -> formatOtherProcess(process)
    }

    fun defaultNumberFormat(value: Double): String = "%.4f".format(value)
    fun formatIngredientRate(
        ingredient: Ingredient,
        rate: Double,
    ): String = defaultNumberFormat(rate) + "/s"

    fun formatInputRate(input: Ingredient, rate: Double): String = formatIngredientRate(input, rate)
    fun formatOutputRate(output: Ingredient, rate: Double): String = formatIngredientRate(output, rate)
    fun formatRealProcessUsage(process: Process, rate: Double): String = defaultNumberFormat(rate)
    fun formatOtherProcessUsage(process: PseudoProcess, rate: Double): String = defaultNumberFormat(rate)
    fun formatSymbolUsage(usage: Double): String = defaultNumberFormat(usage)
    fun formatAnyProcessUsage(
        process: PseudoProcess,
        rate: Double,
    ): String = when (process) {
        is Input -> formatInputRate(process.ingredient, rate)
        is Output -> formatOutputRate(process.ingredient, rate)
        is RealProcess -> formatRealProcessUsage(process.process, rate)
        else -> formatOtherProcessUsage(process, rate)
    }

    fun formatThroughput(ingredient: Ingredient, throughput: Throughput): String = when {
        throughput.consumption == 0.0 -> "(+${formatIngredientRate(ingredient, throughput.production)})"
        throughput.production == 0.0 -> "(-${formatIngredientRate(ingredient, throughput.consumption)})"
        throughput.net.absoluteValue < 1e-5 -> formatIngredientRate(ingredient, throughput.min)
        else -> formatIngredientRate(ingredient, throughput.min) + " (%+f)".format(throughput.net)
    }

    fun formatSurplusIngredient(ingredient: Ingredient): String = formatIngredient(ingredient)
    fun formatIngredient(ingredient: Ingredient): String = formatSymbol(ingredient)
    fun formatSymbol(symbol: Symbol): String = symbol.toString()

    val inputComparator: Comparator<in Ingredient>? get() = ingredientComparator
    val outputComparator: Comparator<in Ingredient>? get() = ingredientComparator
    val otherProcessComparator: Comparator<in PseudoProcess>? get() = null
    val processComparator: Comparator<in Process>? get() = null

    val ingredientComparator: Comparator<in Ingredient>? get() = symbolComparator
    val symbolComparator: Comparator<in Symbol>? get() = compareBy { formatSymbol(it) }

    companion object Default : RecipeLpFormatter
}

private fun <T> Collection<T>.maybeSort(comparator: Comparator<in T>?) =
    if (comparator != null) sortedWith(comparator) else this.toList()

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

fun RecipeSolution.textDisplay(formatter: RecipeLpFormatter = RecipeLpFormatter): String = buildString {
    appendLine("Inputs:")
    val inputList = inputs.keys.maybeSort(formatter.inputComparator)
    displayLeftRight(inputList, formatter::formatInput, { formatter.formatInputRate(it, inputs[it]) })
    appendLine()

    appendLine("Outputs:")
    val outputList = outputs.keys.maybeSort(formatter.outputComparator)
    displayLeftRight(outputList, formatter::formatOutput, { formatter.formatOutputRate(it, outputs[it]) })
    appendLine()

    appendLine("Recipes:")
    val processList = processes.keys.maybeSort(formatter.processComparator)
    displayLeftRight(processList, formatter::formatProcess, { formatter.formatRealProcessUsage(it, processes[it]) })
    appendLine()

    val otherProcessList = otherProcesses.keys.maybeSort(formatter.otherProcessComparator)
    if (otherProcessList.isNotEmpty()) {
        appendLine()
        appendLine("Other processes:")
        displayLeftRight(
            otherProcessList,
            formatter::formatOtherProcess,
            { formatter.formatOtherProcessUsage(it, otherProcesses[it]) })
    }
    if (throughputs.isNotEmpty()) {
        appendLine()
        appendLine("Throughputs:")
        displayLeftRight(
            throughputs.keys.toList().maybeSort(formatter.ingredientComparator),
            formatter::formatIngredient,
            { formatter.formatThroughput(it, throughputs[it]!!) })
    }

    if (surpluses.isNotEmpty()) {
        appendLine()
        appendLine("Surpluses:")
        displayLeftRight(
            surpluses.keys.toList().maybeSort(formatter.ingredientComparator),
            formatter::formatSurplusIngredient,
            { formatter.formatIngredientRate(it, surpluses[it]) })
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
