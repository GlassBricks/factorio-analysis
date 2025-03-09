@file:Suppress("UNCHECKED_CAST")

package glassbricks.recipeanalysis

import it.unimi.dsi.fastutil.doubles.DoubleCollection
import it.unimi.dsi.fastutil.objects.Object2DoubleMap
import kotlin.experimental.ExperimentalTypeInference
import kotlin.math.abs

class AnyVector<T, out Units>
@PublishedApi internal constructor(internal val map: ZeroPutOpenHashMap<T>) : Collection<Object2DoubleMap.Entry<T>> {
    operator fun get(key: T): Double = map.getDouble(key)
    override fun iterator(): Iterator<Object2DoubleMap.Entry<T>> = map.object2DoubleEntrySet().iterator()

    override fun contains(element: Object2DoubleMap.Entry<T>): Boolean = map.containsKey(element.key)
    override fun containsAll(elements: Collection<Object2DoubleMap.Entry<T>>): Boolean =
        elements.all { map.containsKey(it.key) }

    override fun isEmpty(): Boolean = map.isEmpty()
    override val size: Int get() = map.size

    fun toMap(): Map<T, Double> = map.toMap()

    val keys: Set<T> get() = map.keys
    val values: DoubleCollection get() = map.values

    @Suppress("UNCHECKED_CAST")
    fun <U> castUnits(): AnyVector<T, U> = this as AnyVector<T, U>

    operator fun unaryMinus(): AnyVector<T, Units> = mapValues { -it.doubleValue }
    operator fun times(scalar: Double): AnyVector<T, Units> = when (scalar) {
        0.0 -> emptyVector()
        1.0 -> this
        else -> mapValues { it.doubleValue * scalar }
    }

    operator fun times(scalar: Int): AnyVector<T, Units> = this * scalar.toDouble()

    operator fun div(scalar: Double): AnyVector<T, Units> = when {
        scalar.isInfinite() -> emptyVector()
        scalar == 1.0 -> this
        else -> mapValues { it.doubleValue / scalar }
    }

    operator fun div(scalar: Int): AnyVector<T, Units> = this / scalar.toDouble()

    override fun toString(): String = map.object2DoubleEntrySet().joinToString(
        prefix = "MapVector{",
        postfix = "}"
    ) { (ingredient, amount) -> "$ingredient: $amount" }

}

inline fun <T, Units> AnyVector<T, Units>.mapValues(transform: (Object2DoubleMap.Entry<T>) -> Double): AnyVector<T, Units> =
    buildVectorWithUnits {
        for (entry in this@mapValues) {
            this[entry.key] = transform(entry)
        }
    }

fun <T, Units> AnyVector<T, Units>.closeTo(other: AnyVector<T, Units>, tolerance: Double): Boolean =
    all { (ingredient, amount) -> abs(amount - other[ingredient]) <= tolerance }
            && other.all { (ingredient, amount) -> abs(amount - this[ingredient]) <= tolerance }

operator fun <T, U> AnyVector<out T, U>.minus(other: AnyVector<out T, U>): AnyVector<T, U> =
    if (other.isEmpty()) this.relaxKeyType()
    else buildVectorWithUnits {
        this += this@minus
        this -= other
    }

operator fun <T, U> AnyVector<out T, U>.plus(other: AnyVector<out T, U>): AnyVector<T, U> {
    if (other.isEmpty()) return this.relaxKeyType()
    if (this.isEmpty()) return other.relaxKeyType()
    return buildVectorWithUnits {
        this += this@plus
        this += other
    }
}

private val theEmptyVector = AnyVector<Any, Any>(ZeroPutOpenHashMap())
fun <T, U> emptyVector(): AnyVector<T, U> = theEmptyVector as AnyVector<T, U>

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <T, U> AnyVector<out T, U>.relaxKeyType(): AnyVector<T, U> = this as AnyVector<T, U>

operator fun <T, U> Double.times(vector: AnyVector<T, U>): AnyVector<T, U> = vector * this
operator fun <T, U> Int.times(vector: AnyVector<T, U>): AnyVector<T, U> = vector * this.toDouble()

fun <U, T> vectorOfWithUnits(entries: List<Pair<T, Double>>): AnyVector<T, U> = buildVectorWithUnits {
    for ((key, value) in entries) set(key, value)
}

fun <U, T> vectorOfWithUnits(vararg entries: Pair<T, Double>): AnyVector<T, U> = vectorOfWithUnits(entries.asList())

fun <T, U> Map<out T, Double>.toVectorWithUnits(): AnyVector<T, U> = buildVectorWithUnits {
    this.map.putAll(this@toVectorWithUnits)
}

inline fun <T, V> Map<out T, V>.mapValuesToVector(transform: (Map.Entry<T, V>) -> Double): Vector<T> =
    buildVectorWithUnits {
        for (entry in this@mapValuesToVector) {
            val value = transform(entry)
            set(entry.key, value)
        }
    }

typealias Vector<T> = AnyVector<T, Unit>

fun <T> vectorOf(entries: List<Pair<T, Double>>): Vector<T> = vectorOfWithUnits(entries)
fun <T> vectorOf(vararg entries: Pair<T, Double>): Vector<T> = vectorOfWithUnits(entries.asList())
fun <T> Map<out T, Double>.toVector(): Vector<T> = toVectorWithUnits()

fun <T, U> AnyVector<T, U>?.orZero(): AnyVector<T, U> = this ?: emptyVector()

fun <T> uvec(key: T): Vector<T> = vectorOfWithUnits(key to 1.0)

inline fun <T, T2 : Any, U> AnyVector<T, U>.mapKeysNotNull(transform: (T) -> T2?): AnyVector<T2, U> =
    buildVectorWithUnits {
        for ((ingredient, amount) in this@mapKeysNotNull) {
            val newKey = transform(ingredient)
            if (newKey != null) set(newKey, amount)
        }
    }

inline fun <T, R, U> AnyVector<T, U>.mapKeys(transform: (T) -> R): AnyVector<R, U> = buildVectorWithUnits {
    for ((ingredient, amount) in this@mapKeys) {
        set(transform(ingredient), amount)
    }
}

inline fun <T, U> AnyVector<T, U>.filterKeys(predicate: (T) -> Boolean): AnyVector<T, U> = buildVectorWithUnits {
    for ((ingredient, amount) in this@filterKeys) {
        if (predicate(ingredient)) set(ingredient, amount)
    }
}

fun <S, T, U> Iterable<S>.vectorSumOf(transform: (S) -> AnyVector<out T, U>): AnyVector<T, U> =
    buildVectorWithUnits {
        for (s in this@vectorSumOf) {
            this += transform(s)
        }
    }

@JvmInline
value class AnyVectorBuilder<T, U>
@PublishedApi internal constructor(
    @PublishedApi internal val map: ZeroPutOpenHashMap<T>,
) {
    constructor() : this(ZeroPutOpenHashMap())

    operator fun get(key: T): Double = map.getDouble(key)
    operator fun set(key: T, value: Double) {
        map[key] = value
    }

    operator fun plusAssign(other: AnyVector<out T, *>) {
        for ((ingredient, amount) in other) {
            this[ingredient] += amount
        }
    }

    operator fun minusAssign(other: AnyVector<out T, *>) {
        for ((ingredient, amount) in other) {
            this[ingredient] -= amount
        }
    }

    operator fun timesAssign(scalar: Double) {
        for (entry in map) {
            entry.setValue(entry.doubleValue * scalar)
        }
    }

    operator fun timesAssign(scalar: Int) = timesAssign(scalar.toDouble())
    operator fun divAssign(scalar: Double) {
        for (entry in map) {
            entry.setValue(entry.doubleValue / scalar)
        }
    }

    operator fun divAssign(scalar: Int) = divAssign(scalar.toDouble())

    inline fun mapValuesInPlace(transform: (Object2DoubleMap.Entry<T>) -> Double) {
        for (entry in map) {
            entry.setValue(transform(entry))
        }
    }

    fun build(): AnyVector<T, U> {
        if (map.isEmpty()) return emptyVector()
        return AnyVector(map)
    }
}
typealias VectorBuilder<T> = AnyVectorBuilder<T, Unit>

@OptIn(ExperimentalTypeInference::class)
inline fun <T, U> buildVectorWithUnits(
    size: Int = 16,
    @BuilderInference
    block: AnyVectorBuilder<T, U>.() -> Unit,
): AnyVector<T, U> {
    val map = AnyVectorBuilder<T, U>(ZeroPutOpenHashMap(size))
    map.block()
    return map.build()
}

@OptIn(ExperimentalTypeInference::class)
inline fun <T> buildVector(
    size: Int = 16,
    @BuilderInference block: VectorBuilder<T>.() -> Unit,
): Vector<T> =
    buildVectorWithUnits(size, block)
