package glassbricks.recipeanalysis

interface Equals {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

/**
 * Inherits of this type indicate that their instances are identify something uniquely.
 */
interface Symbol : Equals {
    override fun toString(): String
}

class SimpleSymbol(val name: String) : Symbol {
    override fun equals(other: Any?): Boolean = super.equals(other)
    override fun hashCode(): Int = super.hashCode()
    override fun toString(): String = "Symbol($name)"
}

@Suppress("FunctionName")
fun Symbol(name: String): SimpleSymbol = SimpleSymbol(name)
