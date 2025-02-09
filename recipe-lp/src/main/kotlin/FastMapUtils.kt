package glassbricks.recipeanalysis

import it.unimi.dsi.fastutil.objects.Object2DoubleMap

interface ZeroPutMap<T> : Object2DoubleMap<T> {
    operator fun iterator(): Iterator<Object2DoubleMap.Entry<T>>
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> Object2DoubleMap.Entry<T>.component1(): T = key

@Suppress("NOTHING_TO_INLINE")
inline operator fun Object2DoubleMap.Entry<*>.component2(): Double = doubleValue

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> Object2DoubleMap<T>.set(key: T, value: Double) = put(key, value)
