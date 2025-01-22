package glassbricks.recipeanalysis

/**
 * Creates variables and constraints such that:
 * ```
 * for all rows r:
 *    var(r) = sum( var(recipe) * recipe.weight[r] )
 * ```
 */
inline fun <R, K> createMatrixEquations(
    recipeVars: Map<R, Variable>,
    weight: (R) -> MapVector<K, *>,
    createVariable: (K) -> Variable,
    op: ComparisonOp = ComparisonOp.Eq,
): Pair<List<Constraint>, Map<K, Variable>> {
    val elementsByKeys = recipeVars.entries.groupByMulti { weight(it.key).keys }
    val allKeys = elementsByKeys.keys
    val keyToVar = LinkedHashMap<K, Variable>(allKeys.size)
    val constraints = mutableListOf<Constraint>()
    for ((key, entries) in elementsByKeys) {
        val keyVar = createVariable(key)
        keyToVar[key] = keyVar
        val coeffs = buildMap {
            for ((element, elementVar) in entries) {
                this[elementVar] = weight(element)[key]
            }
            this[keyVar] = -1.0
        }
        constraints.add(Constraint(coeffs, op, 0.0))
    }
    return constraints to keyToVar
}

inline fun <T, K> Iterable<T>.groupByMulti(getKeys: (T) -> Iterable<K>): Map<K, List<T>> =
    buildMap<K, MutableList<T>> {
        for (element in this@groupByMulti) {
            for (ingredient in getKeys(element)) {
                this.getOrPut(ingredient, ::mutableListOf).add(element)
            }
        }
    }
