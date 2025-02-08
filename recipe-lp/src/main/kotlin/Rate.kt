package glassbricks.recipeanalysis

import kotlin.time.Duration
import kotlin.time.DurationUnit

// kinda like Duration except we use a Float
@JvmInline
value class Time(val seconds: Double) {
    override fun toString(): String = "${seconds}s"
}

operator fun Time.div(value: Double): Time = Time(seconds / value)

fun Duration.asTime(): Time = Time(toDouble(DurationUnit.SECONDS))

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
operator fun Int.div(time: Time): Rate = Rate(this / time.seconds)
operator fun Double.div(duration: Duration): Rate = this / duration.asTime()
operator fun Int.div(duration: Duration): Rate = this / duration.asTime()
operator fun <T> Vector<T>.div(time: Time): RateVector<T> = (this / time.seconds).castUnits()
