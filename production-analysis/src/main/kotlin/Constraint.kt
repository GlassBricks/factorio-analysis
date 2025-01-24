package glassbricks.recipeanalysis

enum class ComparisonOp {
    Leq, Geq, Eq;

    fun opString() = when (this) {
        Leq -> "<="
        Geq -> ">="
        Eq -> "=="
    }
}

data class KeyedConstraint<T>(val lhs: Map<T, Double>, val op: ComparisonOp, val rhs: Double) {
    override fun toString(): String = lhs.entries.joinToString(
        prefix = "Constraint(",
        postfix = " ${op.opString()} $rhs)",
        separator = " + "
    ) { (variable, coefficient) -> "$variable * $coefficient" }
}
typealias Constraint = KeyedConstraint<Variable>

typealias SymbolConstraint = KeyedConstraint<Symbol>

fun <T, R> KeyedConstraint<T>.mapKeys(transform: (T) -> R): KeyedConstraint<R> =
    KeyedConstraint(lhs.mapKeys { (key) -> transform(key) }, op, rhs)

interface ConstraintDsl<T> {
    val constraints: MutableList<KeyedConstraint<T>>

    infix fun Vector<out T>.leq(rhs: Number) {
        constraints += KeyedConstraint(this.relaxKeyType(), ComparisonOp.Leq, rhs.toDouble())
    }

    infix fun Vector<out T>.eq(rhs: Number) {
        constraints += KeyedConstraint(this.relaxKeyType(), ComparisonOp.Eq, rhs.toDouble())
    }

    infix fun Vector<out T>.geq(rhs: Number) {
        constraints += KeyedConstraint(this.relaxKeyType(), ComparisonOp.Geq, rhs.toDouble())
    }

    infix fun Vector<out T>.leq(rhs: Vector<out T>) = (this - rhs) leq 0
    infix fun Vector<out T>.eq(rhs: Vector<out T>) = (this - rhs) eq 0
    infix fun Vector<out T>.geq(rhs: Vector<out T>) = (this - rhs) geq 0
}

class ConstraintDslImpl<T> : ConstraintDsl<T> {
    override val constraints = mutableListOf<KeyedConstraint<T>>()
}

fun <T> constraints(block: ConstraintDsl<T>.() -> Unit): List<KeyedConstraint<T>> =
    ConstraintDslImpl<T>().apply(block).constraints
