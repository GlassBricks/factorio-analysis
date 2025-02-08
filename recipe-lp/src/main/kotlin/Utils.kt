package glassbricks.recipeanalysis

fun <T> concat(vararg items: Collection<T>): List<T> =
    ArrayList<T>(items.sumOf { it.size }).apply {
        for (item in items) addAll(item)
    }

fun <K, V> Iterable<Map<K, V>>.flattenMaps(): Map<K, V> = buildMap {
    for (map in this@flattenMaps) putAll(map)
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

@PublishedApi
internal inline fun <K, K2, V> Map<K, V>.mapKeysNotNull(transform: (Map.Entry<K, V>) -> K2?): Map<K2, V> {
    val result = LinkedHashMap<K2, V>()
    for (entry in entries) {
        val newKey = transform(entry)
        if (newKey != null) {
            result[newKey] = entry.value
        }
    }
    return result
}
