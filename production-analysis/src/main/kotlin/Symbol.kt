package glassbricks.recipeanalysis

interface Equals {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

/**
 * Inherits of this type indicate that their instances are identify something uniquely.
 */
interface Symbol : Equals {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

data class StrSymbol(val name: String) : Symbol {
    override fun toString(): String = "Symbol($name)"
}

fun Symbol(name: String): StrSymbol = StrSymbol(name)
