package glassbricks.recipeanalysis

/**
 * Any additional constraints.
 *
 * Note: symbols may match those given by [PseudoProcess.additionalCosts]
 */
data class SymbolConstraint(
    val lhs: AmountVector<Symbol>,
    val op: ComparisonOp,
    val rhs: Double,
)

interface ConstraintDsl {
    val constraints: MutableList<SymbolConstraint>
    operator fun SymbolConstraint.unaryPlus() {
        constraints += this
    }

    infix fun AmountVector<out Symbol>.leq(rhs: Number) =
        +SymbolConstraint(this.relaxKeyType(), ComparisonOp.Leq, rhs.toDouble())

    infix fun AmountVector<out Symbol>.eq(rhs: Number) =
        +SymbolConstraint(this.relaxKeyType(), ComparisonOp.Eq, rhs.toDouble())

    infix fun AmountVector<out Symbol>.geq(rhs: Number) =
        +SymbolConstraint(this.relaxKeyType(), ComparisonOp.Geq, rhs.toDouble())

    infix fun AmountVector<out Symbol>.leq(rhs: AmountVector<out Symbol>) = (this - rhs) leq 0
    infix fun AmountVector<out Symbol>.eq(rhs: AmountVector<out Symbol>) = (this - rhs) eq 0
    infix fun AmountVector<out Symbol>.geq(rhs: AmountVector<out Symbol>) = (this - rhs) geq 0
}

fun AmountVector<out Symbol>.constrainLeq(rhs: Double) =
    SymbolConstraint(this.relaxKeyType(), ComparisonOp.Leq, rhs.toDouble())

fun AmountVector<out Symbol>.constrainEq(rhs: Double) =
    SymbolConstraint(this.relaxKeyType(), ComparisonOp.Eq, rhs.toDouble())

fun AmountVector<out Symbol>.constrainGeq(rhs: Double) =
    SymbolConstraint(this.relaxKeyType(), ComparisonOp.Geq, rhs.toDouble())
