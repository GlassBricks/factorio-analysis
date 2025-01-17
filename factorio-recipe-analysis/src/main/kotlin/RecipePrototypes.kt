package me.glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.DataRaw
import glassbricks.factorio.prototypes.ItemFlag
import glassbricks.factorio.prototypes.SpaceAgeDataRaw
import glassbricks.factorio.prototypes.allItemPrototypes
import kotlin.collections.set

class RecipePrototypes(val dataRaw: DataRaw) {
    val qualities = dataRaw.quality.values
        .filter { it.name != "quality-unknown" }
        .sortedBy { it.level.toInt() }

    init {
        for ((prevQ, nextQ) in qualities.zipWithNext()) {
            check(prevQ.next?.value == nextQ.name) { "Quality levels are not contiguous: $prevQ -> $nextQ" }
        }
    }

    val defaultQuality get() = qualities.first()

    val allItems = dataRaw.allItemPrototypes().associate {
        it.name to getItem(it)
    }
    val beacons: Map<String, Beacon>

    init {
        val builtByMap = mutableMapOf<String, Item>()
        for (item in allItems.values) {
            val placeResult = item.prototype.place_result
            if (placeResult.isBlank()) continue
            if (placeResult in builtByMap && item.prototype.flags?.contains(ItemFlag.`primary-place-result`) != true) {
                continue
            }
            builtByMap[placeResult] = item
        }

        this.beacons = dataRaw.beacon.mapValues { Beacon(it.value, builtByMap[it.key]) }
    }

    val beacon get() = beacons.values.first()

    val modules: Map<String, Module> = dataRaw.module.mapValues { Module(it.value) }
}

val SpaceAge by lazy { RecipePrototypes(SpaceAgeDataRaw) }
