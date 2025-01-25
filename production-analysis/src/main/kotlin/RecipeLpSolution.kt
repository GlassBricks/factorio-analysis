package glassbricks.recipeanalysis

private inline fun <T> StringBuilder.displayLeftRight(
    list: List<T>,
    left: (T) -> Any,
    right: (T) -> Double,
) = apply {
    val lefts = list.map { left(it).toString() }
    val leftWidth = lefts.maxOfOrNull { it.length } ?: 0
    for ((el, left) in list.zip(lefts)) {
        append(left)
        repeat(leftWidth - left.length) { append(' ') }
        append(" | ")
        appendLine("%10.5f".format(right(el)))
    }
}

data class RecipeLpSolution(
    val recipeUsage: Vector<PseudoProcess>,
    val surpluses: Vector<Ingredient>,
    val symbolUsage: Vector<Symbol>,
) {
    fun display() = buildString {
        appendLine("Inputs:")
        val inputs = recipeUsage.keys.filterIsInstance<Input>()
        displayLeftRight(inputs, { it.ingredient }) { recipeUsage[it] }
        appendLine()

        appendLine("Outputs:")
        val outputs = recipeUsage.keys.filterIsInstance<Output>()
        displayLeftRight(outputs, { it.ingredient }) { recipeUsage[it] }
        appendLine()

        appendLine("Recipes:")
        val processes = recipeUsage.keys.filterIsInstance<LpProcess>()
        displayLeftRight(processes, { it.process }) { recipeUsage[it] }
        appendLine()

        val otherProcesses = recipeUsage.keys.filter {
            it !is Input && it !is Output && it !is LpProcess
        }
        if (otherProcesses.isNotEmpty()) {
            appendLine()
            appendLine("Other processes:")
            displayLeftRight(otherProcesses, { it }) { recipeUsage[it] }
        }

        if (surpluses.isNotEmpty()) {
            appendLine()
            appendLine("Surpluses:")
            displayLeftRight(surpluses.keys.toList(), { it }) { surpluses[it] }
        }

        if (symbolUsage.isNotEmpty()) {
            appendLine()
            appendLine("Symbols used:")
            displayLeftRight(symbolUsage.keys.toList(), { it }) { symbolUsage[it] }
        }
    }

}

data class RecipeLpResult(
    val lpResult: LpResult,
    val solution: RecipeLpSolution?,
) {
    val lpSolution: LpSolution? get() = lpResult.solution
    val status: LpResultStatus get() = lpResult.status
}
