package glassbricks.recipeanalysis.lp

import glassbricks.recipeanalysis.Vector
import glassbricks.recipeanalysis.relaxKeyType

interface Objective {
    val parent: LpSolver
    operator fun set(variable: Variable, weight: Double)
    operator fun get(variable: Variable): Double
    var maximize: Boolean

}

/** If not in the vector, sets to 0. */
fun Objective.set(weights: Vector<out Variable>, maximize: Boolean = this.maximize) {
    for (variable in parent.variables) {
        this[variable] = weights.relaxKeyType()[variable]
    }
    this.maximize = maximize
}
