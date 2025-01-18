package glassbricks.recipeanalysis

import kotlin.math.abs

@JvmInline
value class MapVector<T, Units>
internal constructor(private val map: Map<T, Double>) : Map<T, Double> by map {
    override operator fun get(key: T): Double = map.getOrDefault(key, 0.0)

    @Suppress("UNCHECKED_CAST")
    fun <U> castUnits(): MapVector<T, U> = this as MapVector<T, U>

    private fun MutableMap<T, Double>.setOrRemove(key: T, amount: Double) {
        if (amount == 0.0) {
            remove(key)
        } else {
            this[key] = amount
        }
    }

    operator fun plus(other: MapVector<T, Units>): MapVector<T, Units> {
        val result = map.toMutableMap()
        for ((key, amount) in other) {
            val amt = result.getOrDefault(key, 0.0) + amount
            result.setOrRemove(key, amt)
        }
        if (result.isEmpty()) return zero()
        return MapVector(result)
    }

    operator fun minus(other: MapVector<T, Units>): MapVector<T, Units> {
        val result = map.toMutableMap()
        for ((ingredient, amount) in other) {
            val amt = result.getOrDefault(ingredient, 0.0) - amount
            result.setOrRemove(ingredient, amt)
        }
        if (result.isEmpty()) return zero()
        return MapVector(result)
    }

    private inline fun mapValues(transform: (Map.Entry<T, Double>) -> Double): MapVector<T, Units> =
        MapVector(map.mapValues { transform(it) })

    operator fun unaryMinus(): MapVector<T, Units> = mapValues { -it.value }

    operator fun times(scalar: Double): MapVector<T, Units> = when (scalar) {
        0.0 -> zero()
        1.0 -> this
        else -> mapValues { it.value * scalar }
    }

    operator fun times(scalar: Int): MapVector<T, Units> = this * scalar.toDouble()

    operator fun div(scalar: Double): MapVector<T, Units> = when {
        scalar.isInfinite() -> zero()
        scalar == 1.0 -> this
        else -> mapValues { it.value / scalar }
    }

    operator fun div(scalar: Int): MapVector<T, Units> = this / scalar.toDouble()

    fun closeTo(other: MapVector<T, Units>, tolerance: Double): Boolean =
        map.all { (ingredient, amount) -> abs(amount - other[ingredient]) <= tolerance }
                && other.all { (ingredient, amount) -> abs(amount - this[ingredient]) <= tolerance }

    override fun toString(): String {
        return map.entries.joinToString(
            prefix = "MapVector{",
            postfix = "}"
        ) { (ingredient, amount) -> "$ingredient: $amount" }
    }

    companion object {
        fun <T, Units> zero() = MapVector<T, Units>(emptyMap())
    }
}

operator fun <T, U> Double.times(vector: MapVector<T, U>): MapVector<T, U> = vector * this
operator fun <T, U> Int.times(vector: MapVector<T, U>): MapVector<T, U> = vector * this.toDouble()

fun <Units, T> vector(vararg entries: Pair<T, Double>): MapVector<T, Units> =
    MapVector(entries.toMap().filterValues { it != 0.0 })

fun <Units, T> vector(map: Map<T, Double>): MapVector<T, Units> =
    MapVector(map.filterValues { it != 0.0 })

fun <Units, T> vectorUnsafe(map: Map<T, Double>): MapVector<T, Units> = MapVector(map)

fun <Units, T> vector(): MapVector<T, Units> = MapVector.zero()

typealias AmountVector<T> = MapVector<T, Unit>

fun <T> amountVector(vararg entries: Pair<T, Double>): AmountVector<T> = vector(entries.toMap())
fun <T> amountVector(map: Map<T, Double>): AmountVector<T> = vector(map)

fun <T> basis(key: T): AmountVector<T> = vector(key to 1.0)
