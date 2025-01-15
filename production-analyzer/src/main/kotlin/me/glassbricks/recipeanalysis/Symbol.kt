package me.glassbricks.recipeanalysis

/**
 * Inherits of this type indicate that their instances are unique identifiers; i.e. equals, hashCode make sense.
 */
interface Symbol {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}
