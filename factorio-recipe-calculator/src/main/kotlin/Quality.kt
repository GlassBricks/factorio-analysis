package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.QualityPrototype

class Quality(
    val prototype: QualityPrototype,
    val nextQuality: Quality?,
) : Comparable<Quality> {
    val level = prototype.level.toInt()
    override fun compareTo(other: Quality): Int = level.compareTo(other.level)
    override fun toString(): String = prototype.name
}

fun loadQualities(qualities: Map<String, QualityPrototype>): Map<String, Quality> {
    val qualityMap = mutableMapOf<String, Quality>()
    fun findQuality(prototype: QualityPrototype): Quality = qualityMap.getOrPut(prototype.name) {
        val nextQuality = prototype.next
            ?.let { findQuality(qualities[it.value]!!) }
        Quality(prototype, nextQuality)
    }
    qualities.values
        .filter { it.name != "quality-unknown" }
        .forEach { findQuality(it) }

    return qualityMap
}
