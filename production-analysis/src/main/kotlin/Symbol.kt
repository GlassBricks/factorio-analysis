package glassbricks.recipeanalysis

interface Equatable {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

/**
 * Inherits of this type indicate that their instances are identify something uniquely.
 */
interface Symbol : Equatable {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}
