package glassbricks.recipeanalysis

import kotlin.math.abs

@JvmInline
value class MapVector<T, out Units>
@PublishedApi
internal constructor(internal val map: Map<T, Double>) : Map<T, Double> by map {
    override operator fun get(key: T): Double = map.getOrDefault(key, 0.0)

    @Suppress("UNCHECKED_CAST")
    fun <U> castUnits(): MapVector<T, U> = this as MapVector<T, U>

    private inline fun mapValues(transform: (Map.Entry<T, Double>) -> Double): MapVector<T, Units> =
        MapVector(map.mapValues { transform(it) })

    operator fun unaryMinus(): MapVector<T, Units> = mapValues { -it.value }

    operator fun times(scalar: Double): MapVector<T, Units> = when (scalar) {
        0.0 -> emptyVector()
        1.0 -> this
        else -> mapValues { it.value * scalar }
    }

    operator fun times(scalar: Int): MapVector<T, Units> = this * scalar.toDouble()

    operator fun div(scalar: Double): MapVector<T, Units> = when {
        scalar.isInfinite() -> emptyVector()
        scalar == 1.0 -> this
        else -> mapValues { it.value / scalar }
    }

    operator fun div(scalar: Int): MapVector<T, Units> = this / scalar.toDouble()

    override fun toString(): String = map.entries.joinToString(
        prefix = "MapVector{",
        postfix = "}"
    ) { (ingredient, amount) -> "$ingredient: $amount" }

    fun display(): String = buildString {
        val ingredientStrs = map.keys.map { it.toString() }
        val maxWidth = ingredientStrs.maxOfOrNull { it.length } ?: 0
        for ((ingredient, amount) in map) {
            append("%-${maxWidth}s: %f\n".format(ingredient, amount))
        }
    }
}

internal fun <T> MutableMap<T, Double>.setOrRemove(key: T, amount: Double) {
    if (amount == 0.0) {
        remove(key)
    } else {
        this[key] = amount
    }
}

fun <T, Units> MapVector<T, Units>.closeTo(other: MapVector<T, Units>, tolerance: Double): Boolean =
    map.all { (ingredient, amount) -> abs(amount - other[ingredient]) <= tolerance }
            && other.all { (ingredient, amount) -> abs(amount - this[ingredient]) <= tolerance }

operator fun <T, U> MapVector<out T, U>.minus(other: MapVector<out T, U>): MapVector<T, U> {
    if (other.isEmpty()) return this.relaxKeyType()
    val result = map.toMutableMap()
    for ((ingredient, amount) in other) {
        val amt = result.getOrDefault(ingredient, 0.0) - amount
        result.setOrRemove(ingredient, amt)
    }
    if (result.isEmpty()) return emptyVector()
    return MapVector(result)
}

operator fun <T, U> MapVector<out T, U>.plus(other: MapVector<out T, U>): MapVector<T, U> {
    if (other.isEmpty()) return this.relaxKeyType()
    if (this.isEmpty()) return other.relaxKeyType()
    val result = map.toMutableMap()
    for ((key, amount) in other) {
        val amt = result.getOrDefault(key, 0.0) + amount
        result.setOrRemove(key, amt)
    }
    if (result.isEmpty()) return emptyVector()
    return MapVector(result)
}

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <T, U> MapVector<out T, U>.relaxKeyType(): MapVector<T, U> = this as MapVector<T, U>

fun <T, U> emptyVector(): MapVector<T, U> = MapVector(emptyMap())

operator fun <T, U> Double.times(vector: MapVector<T, U>): MapVector<T, U> = vector * this
operator fun <T, U> Int.times(vector: MapVector<T, U>): MapVector<T, U> = vector * this.toDouble()

fun <U, T> vectorWithUnits(vararg entries: Pair<T, Double>): MapVector<T, U> =
    MapVector(entries.toMap().filterValues { it != 0.0 })

fun <U, T> vectorWithUnits(map: Map<out T, Double>): MapVector<T, U> {
    val filterMap = map.filterValues { it != 0.0 }
    if (filterMap.isEmpty()) return emptyVector()
    return MapVector(filterMap)
}

fun <U, T> createVectorUnsafe(map: Map<T, Double>): MapVector<T, U> = MapVector(map)

typealias Vector<T> = MapVector<T, Unit>
typealias AnyVector<T> = MapVector<T, *>

fun <T> vector(vararg entries: Pair<T, Double>): Vector<T> = vectorWithUnits(entries.toMap())
fun <T> vector(entries: List<Pair<T, Double>>): Vector<T> = vectorWithUnits(entries.toMap())
fun <T> vector(map: Map<out T, Double>): Vector<T> = vectorWithUnits(map)

fun <T> uvec(key: T): Vector<T> = vectorWithUnits(key to 1.0)

inline fun <T, T2 : Any, U> MapVector<T, U>.vectorMapKeysNotNull(transform: (T) -> T2?): MapVector<T2, U> =
    MapVector(mapKeysNotNull { transform(it.key) })

inline fun <T, U> MapVector<T, U>.vectorFilterKeys(predicate: (T) -> Boolean): MapVector<T, U> =
    MapVector(filterKeys { predicate(it) })
