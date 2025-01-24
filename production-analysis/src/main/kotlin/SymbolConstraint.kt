package glassbricks.recipeanalysis

/**
 * Any additional constraints.
 *
 * Note: symbols may match those given by [PseudoProcess.additionalCosts]
 */
data class SymbolConstraint(
    val lhs: Vector<Symbol>,
    val op: ComparisonOp,
    val rhs: Double,
)

interface ConstraintDsl {
    val constraints: MutableList<SymbolConstraint>
    operator fun SymbolConstraint.unaryPlus() {
        constraints += this
    }

    infix fun Vector<out Symbol>.leq(rhs: Number) =
        +SymbolConstraint(this.relaxKeyType(), ComparisonOp.Leq, rhs.toDouble())

    infix fun Vector<out Symbol>.eq(rhs: Number) =
        +SymbolConstraint(this.relaxKeyType(), ComparisonOp.Eq, rhs.toDouble())

    infix fun Vector<out Symbol>.geq(rhs: Number) =
        +SymbolConstraint(this.relaxKeyType(), ComparisonOp.Geq, rhs.toDouble())

    infix fun Vector<out Symbol>.leq(rhs: Vector<out Symbol>) = (this - rhs) leq 0
    infix fun Vector<out Symbol>.eq(rhs: Vector<out Symbol>) = (this - rhs) eq 0
    infix fun Vector<out Symbol>.geq(rhs: Vector<out Symbol>) = (this - rhs) geq 0
}

fun Vector<out Symbol>.constrainLeq(rhs: Double) =
    SymbolConstraint(this.relaxKeyType(), ComparisonOp.Leq, rhs.toDouble())

fun Vector<out Symbol>.constrainEq(rhs: Double) =
    SymbolConstraint(this.relaxKeyType(), ComparisonOp.Eq, rhs.toDouble())

fun Vector<out Symbol>.constrainGeq(rhs: Double) =
    SymbolConstraint(this.relaxKeyType(), ComparisonOp.Geq, rhs.toDouble())
