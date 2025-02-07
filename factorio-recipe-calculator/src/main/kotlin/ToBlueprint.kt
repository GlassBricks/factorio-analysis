package glassbricks.factorio.recipes

import glassbricks.factorio.prototypes.EntityPrototype
import glassbricks.factorio.prototypes.FurnacePrototype
import glassbricks.factorio.recipes.problem.Solution
import glassbricks.recipeanalysis.recipelp.LpProcess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.*
import java.util.zip.DeflaterOutputStream
import kotlin.math.ceil

/**
 * Stubs for now
 */

@Serializable
data class Position(
    val x: Double,
    val y: Double,
)

@Serializable
data class BlueprintEntity(
    val entity_number: Int,
    val name: String,
    val quality: String? = null,
    val position: Position,
    val items: List<BlueprintInsertPlan>? = null,
    val recipe: String? = null,
    val recipe_quality: String? = null,
)

@Serializable
data class BlueprintInsertPlan(
    val id: ItemIdAndQualityPair,
    val items: ItemInventoryPositions,
)

@Serializable
data class ItemIdAndQualityPair(
    val name: String,
    val quality: String?,
)

@Serializable
data class ItemInventoryPositions(
    val in_inventory: List<InventoryPosition>,
)

@Serializable
data class InventoryPosition(
    val inventory: Int,
    val stack: Int,
    val count: Int? = 1,
)

fun MachineSetup<*>.toBlueprintEntity(
    entityNumber: Int,
    position: Position,
): BlueprintEntity? {
    val machine = machine
    val recipe = recipe as? Recipe

    val items = if (machine !is MachineWithModules<*>) null else {
        moduleItems(machine.moduleSet).let {
            if (machine.prototype is FurnacePrototype) {
                val inputItem = this.recipe.inputs.keys.single { it is Item } as Item
                // add one item to main invetory
                it.plusElement(
                    BlueprintInsertPlan(
                        id = ItemIdAndQualityPair(
                            name = inputItem.prototype.name,
                            quality = inputItem.quality.prototype.name,
                        ),
                        items = ItemInventoryPositions(
                            in_inventory = listOf(
                                InventoryPosition(
                                    inventory = 2,
                                    stack = 0,
                                )
                            )
                        )
                    )
                )
            } else {
                it
            }
        }

    }

    return BlueprintEntity(
        entity_number = entityNumber,
        position = position,
        name = machine.prototype.name,
        quality = machine.quality?.prototype?.name,
        recipe = recipe?.prototype?.name,
        recipe_quality = recipe?.inputQuality?.prototype?.name,
        items = items,
    )

}

private fun moduleItems(set: ModuleSet): List<BlueprintInsertPlan> {
    val modules = set.modules.moduleCounts.flatMap { (module, count) ->
        List(count) {
            ItemIdAndQualityPair(
                name = module.prototype.name,
                quality = module.quality.prototype.name,
            )
        }
    }
    val modulesByItem = modules.groupingBy { it }.eachCount()
    var stack = 0
    return modulesByItem.map { (item, count) ->
        BlueprintInsertPlan(
            id = item,
            items = ItemInventoryPositions(
                in_inventory = List(count) {
                    InventoryPosition(
                        inventory = 4,
                        stack = stack++,
                    )
                }
            )
        )
    }
}

@Serializable
data class BlueprintJson(
    val item: String = "blueprint",
    val entities: List<BlueprintEntity>? = null,
)

fun Map<MachineSetup<*>, Double>.toBlueprint(): BlueprintJson {
    var entityNum = 1
    var curCol = 0.0
    var curHeight = 0.0
    var curRowMaxHeight = 0.0
    val keys = this.keys.sortedBy {
        it.recipe.toString()
    }
    val entities = keys.flatMap { setup ->
        val count = this[setup]!!
        val prototype = setup.machine.prototype as EntityPrototype
        val height = prototype.tile_height ?: prototype.collision_box?.height?.let(::ceil)?.toInt()
        ?: error("No height for $prototype")
        val width = prototype.tile_width ?: prototype.collision_box?.width?.let(::ceil)?.toInt()
        ?: error("No width for $prototype")
        var yOffset = height / 2.0
        List(ceil(count).toInt()) {
            if (yOffset > 30) {
                yOffset = height / 2.0
                curCol += width
            }
            curRowMaxHeight = maxOf(curRowMaxHeight, yOffset + height / 2.0)
            setup.toBlueprintEntity(
                entityNumber = entityNum++,
                position = Position(x = curCol + width / 2.0, y = curHeight + yOffset),
            ).also {
                yOffset += height
            }
        }.also {
            curCol += width
            if (curCol > 150) {
                curCol = 0.0
                curHeight += curRowMaxHeight + 1.0
                curRowMaxHeight = 0.0
            }
        }.filterNotNull()
    }

    return BlueprintJson(entities = entities)
}

internal val bpJson = Json {
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
class BlueprintProxy(
    val blueprint: BlueprintJson,
)

@OptIn(ExperimentalSerializationApi::class)
fun BlueprintJson.exportTo(stream: OutputStream) {
    stream.write('0'.code)
    stream
        .let { Base64.getEncoder().wrap(it) }
        .let { DeflaterOutputStream(it) }
        .use {
            bpJson.encodeToStream(BlueprintProxy(blueprint = this), it)
        }
}

fun BlueprintJson.exportToString(): String {
    val writeStream = ByteArrayOutputStream(1024)
    this.exportTo(writeStream)
    return writeStream.toString()
}

fun BlueprintJson.exportTo(file: File) {
    file.parentFile.mkdirs()
    this.exportTo(file.outputStream().buffered())
}

fun Solution.toBlueprint(): BlueprintJson {
    val machines = this.processes
        .filterKeys { key ->
            key is LpProcess && key.process is MachineSetup<*>
        }
        .mapKeys { (key, _) ->
            (key as LpProcess).process as MachineSetup<*>
        }

    return machines.toBlueprint()
}

fun main() = with(SpaceAge) {

    val machine = craftingMachine("assembling-machine-2").withModules(module("speed-module-2") * 2)
        .processing(recipe("iron-gear-wheel").withQuality(quality("uncommon")))
    var bpEntity = machine.toBlueprintEntity(1, Position(0.0, 0.0))!!
    println(bpEntity)
    println(bpJson.encodeToString<BlueprintEntity>(bpEntity))

    val recycler = craftingMachine("recycler").withModules(module("speed-module-2") * 2)
        .processing(recipe("iron-gear-wheel-recycling").withQuality(quality("uncommon")))
    bpEntity = recycler.toBlueprintEntity(2, Position(0.0, 0.0))!!
    println(bpEntity)
    println(bpJson.encodeToString<BlueprintEntity>(bpEntity))
    println(BlueprintJson(entities = listOf(bpEntity)).exportToString())
}
