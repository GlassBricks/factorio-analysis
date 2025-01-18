package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityWithOwnerPrototype
import glassbricks.factorio.prototypes.QualityPrototype

interface Entity {
    val prototype: EntityWithOwnerPrototype
    val quality: Quality

    fun withQuality(quality: Quality): Entity
}
