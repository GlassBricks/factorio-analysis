package me.glassbricks.recipeanalysis


internal inline fun <K, T, R> Map<K, T>.mapValuesNotNull(transform: (Map.Entry<K, T>) -> R?): Map<K, R> {
    val destination = LinkedHashMap<K, R>()
    for (entry in entries) {
        val newValue = transform(entry)
        if (newValue != null) {
            destination[entry.key] = newValue
        }
    }
    return destination
}
