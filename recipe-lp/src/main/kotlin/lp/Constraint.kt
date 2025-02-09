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
typealias SymbolConstraint = KeyedConstraint<Symbol>

fun <T, R> KeyedConstraint<T>.mapKeys(transform: (T) -> R): KeyedConstraint<R> =
    KeyedConstraint(lhs.mapKeys(transform), op, rhs)

infix fun <T> Vector<out T>.leq(rhs: Double) = KeyedConstraint(this.relaxKeyType(), ComparisonOp.Leq, rhs)
infix fun <T> Vector<out T>.eq(rhs: Double) = KeyedConstraint(this.relaxKeyType(), ComparisonOp.Eq, rhs)
infix fun <T> Vector<out T>.geq(rhs: Double) = KeyedConstraint(this.relaxKeyType(), ComparisonOp.Geq, rhs)

infix fun <T> Vector<out T>.leq(rhs: Vector<out T>) = (this - rhs).leq(0.0)
infix fun <T> Vector<out T>.eq(rhs: Vector<out T>) = (this - rhs).eq(0.0)
infix fun <T> Vector<out T>.geq(rhs: Vector<out T>) = (this - rhs).geq(0.0)

interface Constraint {
    val parent: LpSolver
    operator fun get(variable: Variable): Double
    operator fun set(variable: Variable, value: Double)
    var lb: Double
    var ub: Double

    fun coefficientsAsVector(): Vector<Variable> = buildVector {
        for (variable in parent.variables) {
            this[variable] = this@Constraint[variable]
        }
    }
}

fun Constraint.setCoefficients(weights: Vector<out Variable>) {
    for ((variable, coefficient) in weights) {
        this[variable] = coefficient
    }
}

fun Constraint.setBoundsFromOp(op: ComparisonOp, bound: Double) {
    when (op) {
        ComparisonOp.Leq -> {
            ub = bound; lb = Double.NEGATIVE_INFINITY
        }

        ComparisonOp.Geq -> {
            lb = bound; ub = Double.POSITIVE_INFINITY
        }

        ComparisonOp.Eq -> {
            lb = bound; ub = bound
        }
    }
}

fun LpSolver.addConstraint(
    lhs: Vector<out Variable> = emptyVector(),
    op: ComparisonOp = ComparisonOp.Leq,
    rhs: Double = 0.0,
    name: String = "",
): Constraint = addConstraint(0.0, 0.0, name).apply {
    setCoefficients(lhs)
    setBoundsFromOp(op, rhs)
}

fun LpSolver.addConstraint(
    constraint: KeyedConstraint<Variable>, name: String = "",
): Constraint = addConstraint(constraint.lhs, constraint.op, constraint.rhs, name)

fun LpSolver.addConstraints(constraints: Iterable<KeyedConstraint<Variable>>) {
    for (constraint in constraints) {
        addConstraint(constraint)
    }
}

operator fun LpSolver.plusAssign(constraint: KeyedConstraint<Variable>) {
    addConstraint(constraint)
}

operator fun LpSolver.plusAssign(constraints: Iterable<KeyedConstraint<Variable>>) {
    addConstraints(constraints)
}
