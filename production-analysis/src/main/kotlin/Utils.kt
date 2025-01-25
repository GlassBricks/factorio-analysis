package glassbricks.recipeanalysis

fun <T> concat(vararg items: Collection<T>): List<T> =
    ArrayList<T>(items.sumOf { it.size }).apply {
        for (item in items) addAll(item)
    }

internal inline fun <K, T, R> Map<K, T>.mapValuesNotNull(transform: (Map.Entry<K, T>) -> R?): Map<K, R> {
    val result = LinkedHashMap<K, R>()
    for (entry in entries) {
        val newValue = transform(entry)
        if (newValue != null) {
            result[entry.key] = newValue
        }
    }
    return result
}
