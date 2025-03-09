package glassbricks.factorio.recipes

import java.util.*

data class Shorthand(
    val property: String,
    val type: String,
    val function: String,
    val protoName: String,
)

fun toCamelCase(s: String): String {
    return s.split("-")
        .let {
            it.first() + it.drop(1)
                .joinToString("") { it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
        }
}

fun main() = with(SpaceAge) {
    val shorthands = mutableMapOf<String, Shorthand>()

    fun <K, T : Any> add(
        fnName: String,
        map: Map<K, T>,
        suffix: String = "",
    ) {
        for ((protoName, _) in map) {
            val protoName = protoName.toString()
            val propName = toCamelCase(protoName) + suffix
            shorthands[propName] = Shorthand(
                property = propName,
                type = fnName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                function = fnName,
                protoName = protoName,
            )
        }
    }

    // lowest to highest priority
    add("recipe", recipes)
    add("item", items)
    add("fluid", fluids)
    add("module", modules)
    add("craftingMachine", craftingMachines)
    add("miningDrill", miningDrills)
    add("resource", resources, suffix = "Mining")
    add("quality", qualityMap)

    // val WithFactorioPrototypes.$propName: $type get() = $fnName("$protoName")
    val result = buildString {
        for (v in shorthands.values) {
            val propName = v.property
            val typeName = v.type
            val functionName = v.function
            val protoName = v.protoName
            appendLine("val WithFactorioPrototypes.$propName: $typeName get() = $functionName(\"$protoName\")")
        }
    }
    println(result)
    // to clipboard
    java.awt.Toolkit.getDefaultToolkit()
        .systemClipboard
        .setContents(java.awt.datatransfer.StringSelection(result), null)
    Thread.sleep(10)
}
