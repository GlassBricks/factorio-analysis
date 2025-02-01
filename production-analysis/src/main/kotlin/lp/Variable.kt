package glassbricks.recipeanalysis.lp

enum class VariableType {
    Continuous,
    Integer,

    /**
     * Either 0, or within a range.
     *
     * An auxiliary variable will be used for this.
     */
    SemiContinuous,
}

class Variable(
    val name: String,
    val lowerBound: Double = Double.NEGATIVE_INFINITY,
    val upperBound: Double = Double.POSITIVE_INFINITY,
    val type: VariableType = VariableType.Continuous,
) {
    override fun toString(): String = "Variable($name)"
}

data class VariableConfig(
    val lowerBound: Double = 0.0,
    val upperBound: Double = Double.POSITIVE_INFINITY,
    val type: VariableType = VariableType.Continuous,
    val cost: Double = 0.0,
) {
    fun createVariableNoCost(name: String): Variable = Variable(
        name = name,
        lowerBound = lowerBound,
        upperBound = upperBound,
        type = type,
    )

    override fun toString(): String = buildString {
        append("VariableConfig(")
        var hasPrev = false
        fun appendComma() = apply {
            if (hasPrev) append(", ")
            hasPrev = true
        }
        if (lowerBound != 0.0) appendComma().append("lowerBound=$lowerBound")
        if (upperBound != Double.POSITIVE_INFINITY) appendComma().append("upperBound=$upperBound")
        if (type != VariableType.Continuous) appendComma().append("type=$type")
        if (cost != 0.0) appendComma().append("cost=$cost")
        append(")")
    }
}

data class VariableConfigBuilder(
    var lowerBound: Double = 0.0,
    var upperBound: Double = Double.POSITIVE_INFINITY,
    var type: VariableType = VariableType.Continuous,
    var cost: Double = 0.0,
) {
    fun build(): VariableConfig = VariableConfig(
        lowerBound = lowerBound,
        upperBound = upperBound,
        type = type,
        cost = cost,
    )
}
