package glassbricks.recipeanalysis

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.runInterruptible
import kotlin.time.Duration

interface IncrementalSolver<out R> {
    fun runFor(maxDuration: Duration): R
    val canSolveMore: Boolean
    val lastResult: R?
}

abstract class AbstractIncrementalSolver<R> : IncrementalSolver<R> {
    final override var lastResult: R? = null
        private set
    final override var canSolveMore: Boolean = true
        private set

    private val isRunning = atomic(false)

    override fun runFor(maxDuration: Duration): R {
        if (!isRunning.compareAndSet(expect = false, update = true)) error("Solver is already running")
        if (!canSolveMore) return lastResult!!
        try {
            val (result, hasMore) = doSolveFor(maxDuration)
            canSolveMore = hasMore
            return result
        } finally {
            isRunning.value = false
        }
    }

    protected abstract fun doSolveFor(duration: Duration): Pair<R, Boolean>
}

inline fun <T, R> IncrementalSolver<T>.map(crossinline transform: (T) -> R): IncrementalSolver<R> =
    object : IncrementalSolver<R> {
        override val canSolveMore: Boolean get() = this@map.canSolveMore
        override var lastResult: R? = null
        override fun runFor(maxDuration: Duration): R {
            if (!canSolveMore) return lastResult!!
            return transform(this@map.runFor(maxDuration))
                .also { lastResult = it }
        }
    }

fun <R> IncrementalSolver<R>.asFlow(solutionInterval: Duration): Flow<R> = channelFlow {
    while (canSolveMore) {
        val result = runInterruptible(Dispatchers.IO) {
            runFor(solutionInterval)
        }
        send(result)
    }
}
