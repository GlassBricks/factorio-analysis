package glassbricks.recipeanalysis.lp

interface Variable {
    val name: String
    var lowerBound: Double
    var upperBound: Double
    val type: VariableType
    var objectiveWeight: Double
}

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

fun LpSolver.addPositiveVariable(
    name: String = "",
    cost: Double = 0.0,
    type: VariableType = VariableType.Continuous,
): Variable = addVariable(
    lowerBound = 0.0,
    upperBound = Double.POSITIVE_INFINITY,
    type = type,
    cost = cost,
    name = name,
)

data class VariableConfig(
    val lowerBound: Double = 0.0,
    val upperBound: Double = Double.POSITIVE_INFINITY,
    val type: VariableType = VariableType.Continuous,
    val cost: Double = 0.0,
) {
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

fun LpSolver.addVariable(config: VariableConfig, name: String = ""): Variable = addVariable(
    lowerBound = config.lowerBound,
    upperBound = config.upperBound,
    type = config.type,
    cost = config.cost,
    name = name,
)
