package glassbricks.recipeanalysis.lp

import glassbricks.recipeanalysis.*

enum class ComparisonOp {
    Leq, Geq, Eq;

    fun opString() = when (this) {
        Leq -> "<="
        Geq -> ">="
        Eq -> "=="
    }
}

data class KeyedConstraint<out T>(val lhs: Vector<out T>, val op: ComparisonOp, val rhs: Double) {
    override fun toString(): String = lhs.joinToString(
        prefix = "Constraint(",
        postfix = " ${op.opString()} $rhs)",
        separator = " + "
    ) { (variable, coefficient) -> "$variable * $coefficient" }
}
typealias Constraint = KeyedConstraint<Variable>
typealias SymbolConstraint = KeyedConstraint<Symbol>

fun <T, R> KeyedConstraint<T>.mapKeys(transform: (T) -> R): KeyedConstraint<R> =
    KeyedConstraint(lhs.mapKeys(transform), op, rhs)

infix fun <T> Vector<out T>.leq(rhs: Double) = KeyedConstraint(this.relaxKeyType(), ComparisonOp.Leq, rhs)
infix fun <T> Vector<out T>.eq(rhs: Double) = KeyedConstraint(this.relaxKeyType(), ComparisonOp.Eq, rhs)
infix fun <T> Vector<out T>.geq(rhs: Double) = KeyedConstraint(this.relaxKeyType(), ComparisonOp.Geq, rhs)

infix fun <T> Vector<out T>.leq(rhs: Vector<out T>) = (this - rhs).leq(0.0)
infix fun <T> Vector<out T>.eq(rhs: Vector<out T>) = (this - rhs).eq(0.0)
infix fun <T> Vector<out T>.geq(rhs: Vector<out T>) = (this - rhs).geq(0.0)
