package glassbricks.factorio.prototypecodegen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.reflect.KClass

val packageName = "glassbricks.factorio.prototypes"
private fun String.toClassName() = ClassName(packageName, this)

data class BuiltinType(
    val klass: KClass<*>,
    val placeholderValue: String? = null,
) {
    val isInteger
        get() = klass in listOf(
            Byte::class, Short::class, Int::class, Long::class,
        )
    val isUInteger
        get() = klass in listOf(
            UByte::class, UShort::class, UInt::class, ULong::class,
        )

}

val builtins = mapOf(
    "bool" to BuiltinType(Boolean::class, "false"),
    "double" to BuiltinType(Double::class, "0.0"),
    "float" to BuiltinType(Float::class, "0f"),
    "int8" to BuiltinType(Byte::class, "0"),
    "int16" to BuiltinType(Short::class, "0"),
    "int32" to BuiltinType(Int::class, "0"),
    "int64" to BuiltinType(Long::class, "0"),
    "uint8" to BuiltinType(UByte::class, "0u"),
    "uint16" to BuiltinType(UShort::class, "0u"),
    "uint32" to BuiltinType(UInt::class, "0u"),
    "uint64" to BuiltinType(ULong::class, "0u"),
    "string" to BuiltinType(String::class, """"""""),
)
val omit = setOf("DataExtendMethod")


class Generator(apiDocs: PrototypeApiDocs) {
    private data class PrototypeData(val prototype: Prototype)

    private val prototypes = process(apiDocs.prototypes) { PrototypeData(it) }

    private data class ConceptData(val concept: Concept)

    private val conceptData = process(apiDocs.types) { ConceptData(it) }

    private fun generateConcept(conceptData: ConceptData): Any? {
        val concept = conceptData.concept
        return if (concept.name in builtins) null else {
            val resultType = transformType(concept.type, TypeContext.OnConcept(concept)).typeName
            TypeAliasSpec.builder(concept.name, resultType).apply {
                addDocumentation(concept.description)
            }.build()
        }
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

            for (property in prototype.properties) {
                addProperty(generatePrototypeProperty(prototype, property))
            }
        }
        return type.build()
    }

    private sealed interface TypeContext {
        class OnConcept(val concept: Concept) : TypeContext
        class OnProperty(val parent: ProtoOrConcept, val property: Property) : TypeContext
        class InnerType(val parentContext: TypeContext, val sourceType: TypeDefinition) : TypeContext
    }

    private tailrec fun TypeContext.rootCtx(): TypeContext = when (this) {
        is TypeContext.InnerType -> parentContext.rootCtx()
        else -> this
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
                    builtins[value]!!.klass.asTypeName().toMappedType()
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
                    transformType(apiType.key, innerCtx()).typeName,
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

    private fun generatePropertyCommon(parent: ProtoOrConcept, property: Property): PropertySpec.Builder {
        val type = transformType(property.type, TypeContext.OnProperty(parent, property)).typeName
            .copy(nullable = property.optional)
        return PropertySpec.builder(property.name, type).apply {
            addDocumentation(property.description)
        }
    }

    private fun generatePrototypeProperty(prototype: Prototype, property: Property): PropertySpec =
        generatePropertyCommon(prototype, property).apply {
            val defaultValue = property.default?.let { transformDefaultValue(it, property.type) }
            if (defaultValue != null) {
                initializer(defaultValue)
            } else if (property.optional) {
                initializer("null")
            } else property.type.findBuiltinType()?.placeholderValue?.let {
                initializer(CodeBlock.of("required(%L)", it))
            } ?: run {
                addModifiers(KModifier.LATEINIT)
            }

            mutable()
            // protected set
            setter(FunSpec.setterBuilder().addModifiers(KModifier.PROTECTED).build())
        }.build()

    private tailrec fun TypeDefinition.findBuiltinType(): BuiltinType? = when {
        this is TypeType -> value.findBuiltinType()
        this is LiteralType -> when {
            value.isString -> builtins["string"]
            value.booleanOrNull != null -> builtins["bool"]
            else -> value.longOrNull?.let {
                when (it) {
                    in Int.MIN_VALUE..Int.MAX_VALUE -> builtins["int32"]
                    else -> builtins["int64"]
                }
            } ?: value.doubleOrNull?.let {
                when {
                    it in Float.MIN_VALUE.toDouble()..Float.MAX_VALUE.toDouble() -> builtins["float"]
                    else -> builtins["double"]
                }
            }
        }

        this !is BasicType -> null
        value in builtins -> builtins[value]
        else -> conceptData[value]?.concept?.type?.findBuiltinType()
    }

    private fun transformDefaultValue(default: DefaultValue, type: TypeDefinition): CodeBlock? = when (default) {
        is DescriptionDefault -> null
        is ManualDefault -> default.value
        is LiteralDefault -> {
            val builtinType = type.findBuiltinType()
            val value = default.value
            if (builtinType?.klass == String::class) {
                CodeBlock.of("%S", value.content)
            } else if (value.isString) {
                println("TODO: String literal on non-string type")
                CodeBlock.of("%S", value.content)
            } else value.booleanOrNull?.let {
                require(builtinType?.klass == Boolean::class)
                CodeBlock.of("%L", it)
            } ?: value.doubleOrNull?.let {
                when {
                    builtinType == null -> error("No builtin type for literal value: $value")
                    builtinType.klass == Double::class -> CodeBlock.of("%L", it)
                    builtinType.klass == Float::class -> CodeBlock.of("%Lf", it)
                    builtinType.isInteger -> CodeBlock.of("%L", it.toLong())
                    builtinType.isUInteger -> CodeBlock.of("%Lu", it.toLong())
                    else -> error("Unhandled literal value: $value")
                }
            } ?: error("Unhandled literal value: $value")
        }
    }

    private fun newFileBuilder(name: String) = FileSpec.builder(packageName, name).apply {
        addKotlinDefaultImports()

        val suprer

    }

    fun generate(): List<FileSpec> {
        val prototypesFile: FileSpec.Builder = FileSpec.builder(packageName, "Prototypes")
        for (prototype in prototypes.values) {
            prototypesFile.addType(generatePrototype(prototype))
        }

        val conceptsFile = FileSpec.builder(packageName, "Concepts")
        for (concept in conceptData.values) {
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
