package glassbricks.recipeanalysis

@JvmInline
value class Time(val seconds: Double)

/**
 * Inverse of time
 */
@JvmInline
value class Rate(val seconds: Double)

typealias RateVector<T> = MapVector<T, Rate>

fun <T> rateVector(vararg pairs: Pair<T, Double>): RateVector<T> = vector(pairs.toMap())
fun <T> rateVector(map: Map<T, Double>): RateVector<T> = vector(map)

operator fun Double.div(time: Time): Rate = Rate(this / time.seconds)
operator fun <T> AmountVector<T>.div(time: Time): RateVector<T> = (this / time.seconds).castUnits()
