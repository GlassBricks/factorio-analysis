package glassbricks.factorio.prototypecodegen


import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.KProperty

@Suppress("NOTHING_TO_INLINE")
inline fun <T> required(value: T): T = value


val FactorioJson: Json = Json { ignoreUnknownKeys = true }

abstract class JsonObjRepr(protected val jsonSource: JsonObject) {
    protected val decodedValues: HashMap<String, Any?> = hashMapOf<String, Any?>()

    protected inline fun <reified T> get(name: String): T = decodedValues.computeIfAbsent(name) {
        jsonSource[name]?.let { jsonEl -> FactorioJson.decodeFromJsonElement<T>(jsonEl) }
    } as T

    protected inline fun <reified T> get(name: String, default: T): T = decodedValues.computeIfAbsent(name) {
        val jsonEl = jsonSource[name]
        if (jsonEl == null) default else FactorioJson.decodeFromJsonElement<T>(jsonEl)
    } as T

    protected inline operator fun <reified T> getValue(thisRef: Any?, property: KProperty<*>): T = get(property.name)
}

@Serializable
class Position(val x: Double, val y: Double)

class Test(json: JsonObject) : JsonObjRepr(json) {
    val thing: Position by this
}

fun main() {
    val json = FactorioJson.parseToJsonElement("""{"thing": {"x": 1.0, "y": 2.0}}""").jsonObject
    val test = Test(json)
    println(test.thing)
}