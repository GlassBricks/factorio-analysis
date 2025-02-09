package glassbricks.recipeanalysis.recipelp

import glassbricks.recipeanalysis.*
import glassbricks.recipeanalysis.lp.VariableConfig
import glassbricks.recipeanalysis.lp.VariableConfigBuilder

/**
 * Can be:
 * - [Input], in absolute amounts (given) or with cost per unit (minimize)
 * - [Output], in absolute amounts (required) or with weight per unit (maximize)
 * - An actual [RealProcess] that turns inputs into outputs, possibly with constraints
 */
interface PseudoProcess {
    val ingredientRate: IngredientRate
    val variableConfig: VariableConfig

    val additionalCosts: Vector<Symbol> get() = emptyVector()

    /**
     * If set, a second variable >= the recipe's variable will be created to represent the cost.
     *
     * Additional costs will be associated with this variable, not the recipe's variable.
     */
    val costVariableConfig: VariableConfig?
    val symbol: Symbol?
}

private fun StringBuilder.commonToString(process: PseudoProcess) {
//    if (process.variableConfig != VariableConfig()) append(", variableConfig=").append(process.variableConfig)
//    if (process.additionalCosts.isNotEmpty()) append(", additionalCosts=").append(process.additionalCosts)
//    if (process.costVariableConfig != null) append(", costVariableConfig=").append(process.costVariableConfig)
    if (process.symbol != null) append(", symbol=").append(process.symbol)
}

class RealProcess(
    val process: Process,
    override val variableConfig: VariableConfig = VariableConfig(),
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val costVariableConfig: VariableConfig? = null,
    override val symbol: Symbol? = null,
) : PseudoProcess {
    override val ingredientRate: IngredientRate get() = process.netRate

    override fun toString(): String = buildString {
        append("LpProcess(")
        append(process)
        commonToString(this@RealProcess)
        append(")")
    }
}

class Input(
    val ingredient: Ingredient,
    override val variableConfig: VariableConfig,
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val costVariableConfig: VariableConfig? = null,
    override val symbol: Symbol? = null,
) : PseudoProcess {
    override val ingredientRate: IngredientRate get() = vectorOfWithUnits(ingredient to 1.0)
    override fun toString(): String = buildString {
        append("Input(")
        append(ingredient)
        commonToString(this@Input)
        append(")")
    }
}

class Output(
    val ingredient: Ingredient,
    override val variableConfig: VariableConfig,
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val costVariableConfig: VariableConfig? = null,
    override val symbol: Symbol? = null,
) : PseudoProcess {
    init {
        require(variableConfig.cost <= 0.0) { "Output cost must be negative (to optimize for!)" }
    }

    override val ingredientRate: IngredientRate get() = vectorOfWithUnits(ingredient to -1.0)

    override fun toString(): String = buildString {
        append("Output(")
        append(ingredient)
        commonToString(this@Output)
        append(")")
    }
}

class CustomProcess(
    val name: String,
    override val ingredientRate: IngredientRate,
    override val variableConfig: VariableConfig,
    override val costVariableConfig: VariableConfig? = null,
    override val additionalCosts: Vector<Symbol> = emptyVector(),
    override val symbol: Symbol? = null,
) : PseudoProcess {
    override fun toString(): String = buildString {
        append("CustomProcess(")
        append(name)
        commonToString(this@CustomProcess)
        append(")")
    }
}

class CustomProcessBuilder(val name: String) {
    var ingredientRate: IngredientRate = emptyVector()
    var additionalCosts: Vector<Symbol> = emptyVector()
    val variableConfig = VariableConfigBuilder()
    var symbol: Symbol? = null
    fun build(): CustomProcess = CustomProcess(
        name = name,
        symbol = symbol,
        ingredientRate = ingredientRate,
        additionalCosts = additionalCosts,
        variableConfig = variableConfig.build(),
    )
}
