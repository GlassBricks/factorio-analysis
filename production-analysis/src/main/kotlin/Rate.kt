package glassbricks.recipeanalysis

// kinda like Duration except we use a Float
@JvmInline
value class Time(val seconds: Double)

operator fun Time.div(value: Double): Time = Time(seconds / value)

/**
 * Inverse of time
 */
@JvmInline
value class Rate(val perSecond: Double) {
    companion object {
        val zero: Rate = Rate(0.0)
        val infinity: Rate = Rate(Double.POSITIVE_INFINITY)
    }
}

val Double.perSecond: Rate get() = Rate(this)
val Int.perSecond: Rate get() = Rate(this.toDouble())
val Double.perMinute: Rate get() = Rate(this / 60.0)
val Int.perMinute: Rate get() = Rate(this.toDouble() / 60.0)

typealias RateVector<T> = MapVector<T, Rate>

fun <T> rateVector(vararg pairs: Pair<T, Double>): RateVector<T> = vectorWithUnits(pairs.toMap())
fun <T> rateVector(map: Map<T, Double>): RateVector<T> = vectorWithUnits(map)

operator fun Double.div(time: Time): Rate = Rate(this / time.seconds)
operator fun <T> Vector<T>.div(time: Time): RateVector<T> = (this / time.seconds).castUnits()
