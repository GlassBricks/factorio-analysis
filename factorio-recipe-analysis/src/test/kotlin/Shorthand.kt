package glassbricks.factorio.recipes

data class Shorthand(
    val propName: String,
    val typeName: String,
    val functionName: String,
    val protoName: String,
)

fun toCamelCase(s: String): String {
    return s.split("-")
        .let {
            it.first() + it.drop(1).joinToString("") { it.capitalize() }
        }
}

fun main() = with(SpaceAge) {
    // key by protoName
    val shorthands = mutableMapOf<String, Shorthand>()
    fun add(
        fnName: String,
        typeName: String,
        protoName: String,
    ) {
        shorthands[protoName] = Shorthand(
            propName = toCamelCase(protoName),
            typeName = typeName,
            protoName = protoName,
            functionName = fnName
        )
    }

    fun <K, T : Any> add(
        fnName: String,
        map: Map<K, T>,
    ) {
        for ((protoName, el) in map) add(
            fnName = fnName,
            typeName = fnName.capitalize(),
            protoName = protoName.toString(),
        )
    }

    // lowest to highest priority
    add("recipe", recipes)
    add("item", items)
    add("fluid", fluids)
    add("module", modules)
    add("craftingMachine", craftingMachines)
    add("miningDrill", miningDrills)
    add("quality", qualityMap)

    // val WithFactorioPrototypes.$propName: $type get() = $fnName("$protoName")
    val result = buildString {
        for ((propName, typeName, functionName, protoName) in shorthands.values) {
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
