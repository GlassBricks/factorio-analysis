package glassbricks.recipeanalysis

class Variable(
    val name: String,
    val lowerBound: Double = Double.NEGATIVE_INFINITY,
    val upperBound: Double = Double.POSITIVE_INFINITY,
    val integral: Boolean = false,
) {
    override fun toString(): String = "Variable($name)"
}

data class VariableConfig(
    val lowerBound: Double = 0.0,
    val upperBound: Double = Double.POSITIVE_INFINITY,
    val integral: Boolean = false,
    val cost: Double = 0.0,
) {
    fun createVariable(name: String): Variable = Variable(
        name = name,
        lowerBound = lowerBound,
        upperBound = upperBound,
        integral = integral
    )
}

data class VariableConfigBuilder(
    var lowerBound: Double = 0.0,
    var upperBound: Double = Double.POSITIVE_INFINITY,
    var integral: Boolean = false,
    var cost: Double = 0.0,
) {
    fun build(): VariableConfig = VariableConfig(
        lowerBound = lowerBound,
        upperBound = upperBound,
        integral = integral,
        cost = cost
    )
}
