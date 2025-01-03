package glassbricks.factorio.prototypecodegen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

val packageName = "glassbricks.factorio.prototypes"
private fun String.toClassName() = ClassName(packageName, this)

val builtins = mapOf(
    "bool" to Boolean::class.asTypeName(),
    "double" to Double::class.asTypeName(),
    "float" to Float::class.asTypeName(),
    "int8" to Byte::class.asTypeName(),
    "int16" to Short::class.asTypeName(),
    "int32" to Int::class.asTypeName(),
    "int64" to Long::class.asTypeName(),
    "string" to String::class.asTypeName(),
    "uint8" to UByte::class.asTypeName(),
    "uint16" to UShort::class.asTypeName(),
    "uint32" to UInt::class.asTypeName(),
    "uint64" to ULong::class.asTypeName(),
)
val omit = setOf("DataExtendMethod")



class Generator(apiDocs: PrototypeApiDocs) {


    private data class PrototypeData(val prototype: Prototype)

    private val prototypes = process(apiDocs.prototypes) { PrototypeData(it) }

    private data class ConceptData(val concept: Concept)

    private val conceptData = process(apiDocs.types) { ConceptData(it) }

    private fun generateConcept(concept: Concept): Any? =
        if (concept.name in builtins) null else {
            val resultType = transformType(concept.type, TypeContext.OnConcept(concept)).typeName
            val type = TypeAliasSpec.builder(concept.name, resultType).apply {
                addDocumentation(concept.description)
            }
            type.build()
        }

    private fun generatePrototype(prototypeData: PrototypeData): TypeSpec {
        val prototype = prototypeData.prototype
        val type = TypeSpec.classBuilder(prototype.name).apply {
            addDocumentation(prototype.description)

            if (prototype.abstract) {
                addModifiers(KModifier.ABSTRACT)
            } else {
                addModifiers(KModifier.OPEN)
            }

            if (prototype.parent !== null) {
                superclass(prototype.parent.toClassName())
            }
        }
        return type.build()
    }

    private class TransformedType(val typeName: TypeName)

    private fun transformType(
        apiType: TypeDefinition,
        context: TypeContext
    ): TransformedType {
        fun TypeName.toMappedType() = TransformedType(this)
        fun innerCtx() = TypeContext.InnerType(context, apiType)

        return when (apiType) {
            is BasicType -> {
                val value = apiType.value

                if (value in builtins) {
                    builtins[value]!!.toMappedType()
                } else {
                    check(value in prototypes || value in conceptData) {
                        "Unknown basic type: $value"
                    }
                    value.toClassName().toMappedType()
                }
            }

            is TypeType -> transformType(apiType.value, innerCtx())
            is ArrayType ->
                List::class.asTypeName()
                    .parameterizedBy(transformType(apiType.value, innerCtx()).typeName)
                    .toMappedType()

            is DictType -> Map::class.asTypeName()
                .parameterizedBy(
                    transformType(apiType.key,innerCtx()).typeName,
                    transformType(apiType.value, innerCtx()).typeName
                )
                .toMappedType()

            is LiteralType -> {
                println("LiteralType: $apiType")
                Any::class.asTypeName().toMappedType()
            }

            StructType -> {
                println("TODO: StructType")
                Any::class.asTypeName().toMappedType()
            }

            is TupleType -> {
                println("TODO: TupleType")
                Any::class.asTypeName().toMappedType()
            }

            is UnionType -> {
                println("TODO: UnionType")
                Any::class.asTypeName().toMappedType()
            }
        }
    }

    fun generate(): List<FileSpec> {
        val prototypesFile: FileSpec.Builder = FileSpec.builder(packageName, "Prototypes")
        for (prototype in prototypes.values) {
            prototypesFile.addType(generatePrototype(prototype))
        }

        val conceptsFile = FileSpec.builder(packageName, "Concepts")
        for ((concept) in conceptData.values) {
            when (val result = generateConcept(concept)) {
                null -> {}
                is TypeAliasSpec -> conceptsFile.addTypeAlias(result)
                is TypeSpec -> prototypesFile.addType(result)
                else -> error("Unexpected result type: $result")
            }
        }

        return listOf(prototypesFile.build(), conceptsFile.build())
    }
}


private sealed interface TypeContext  {
    class OnConcept(val concept: Concept) : TypeContext
    class OnProperty(val property: Property) : TypeContext
    class InnerType(val parentContext: TypeContext, val sourceType: TypeDefinition) : TypeContext

}
private tailrec fun TypeContext.rootCtx(): TypeContext = when (this) {
    is TypeContext.InnerType -> parentContext.rootCtx()
    else -> this
}

private fun <T : ProtoOrConcept, V> process(
    items: List<T>,
    mapValues: (T) -> V,
): Map<String, V> = items.sortedBy { it.order }
    .filter { it.name !in omit }
    .associate { it.name to mapValues(it) }

fun Documentable.Builder<*>.addDocumentation(doc: String?) {
    if (!doc.isNullOrBlank()) {
        addKdoc("%L", doc)
    }
}
