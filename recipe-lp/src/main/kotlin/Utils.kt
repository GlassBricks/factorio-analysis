package glassbricks.recipeanalysis

fun <T> concat(vararg items: Collection<T>): List<T> =
    ArrayList<T>(items.sumOf { it.size }).apply {
        for (item in items) addAll(item)
    }
