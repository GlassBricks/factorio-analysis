package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityWithOwnerPrototype
import glassbricks.factorio.prototypes.QualityPrototype

interface Entity {
    val prototype: EntityWithOwnerPrototype
    val quality: QualityPrototype

    fun withQuality(quality: QualityPrototype): Entity
}
