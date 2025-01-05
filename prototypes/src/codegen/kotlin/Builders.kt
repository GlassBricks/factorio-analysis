package glassbricks.factorio.prototypecodegen

import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

class SealedIntf(
    val name: String,
    val superTypes: List<String>,
    val subtypes: Set<String>,
    val modify: (TypeSpec.Builder.() -> Unit)?
)

class GeneratedPrototypes(
    val prototypes: Map<String, GeneratedPrototype>,
    val concepts: Map<String, GeneratedConcept>,
    val extraSealedIntfs: List<SealedIntf>,
    val allSubclassGetters: List<String>,
    val builtins: Map<String, TypeName>,
    val predefined: Map<String, TypeName>
)

sealed interface GeneratedValue {
    val inner: ProtoOrConcept
    val includedProperties: Map<String, PropertyOptions>
    val typeName: String?
    val modify: (TypeSpec.Builder.() -> Unit)?
}

class GeneratedPrototype(
    override val inner: Prototype,
    override val includedProperties: Map<String, PropertyOptions>,
    override val modify: (TypeSpec.Builder.() -> Unit)?
) : GeneratedValue {
    override val typeName: String? = inner.typename
}

class GeneratedConcept(
    override val inner: Concept,
    val overrideType: Pair<TypeName, TypeSpec?>?,
    val innerEnumName: String?,
    override val includedProperties: Map<String, PropertyOptions>,
    override val typeName: String?,
    override val modify: (TypeSpec.Builder.() -> Unit)?,
    var isSealedIntf: Boolean
) : GeneratedValue


@DslMarker
annotation class GeneratedPrototypesDsl

@GeneratedPrototypesDsl
class PropertyOptions(
    val inner: Property,
    var overrideType: TypeName? = null,
    var innerEnumName: String? = null,
    var modify: (PropertySpec.Builder.() -> Unit)? = null
)

@GeneratedPrototypesDsl
class GeneratedPrototypesBuilder(docs: PrototypeApiDocs) {
    val origPrototypes = docs.prototypes.associateBy { it.name }
    val origConcepts = docs.types.associateBy { it.name }
    val prototypes = mutableMapOf<String, GeneratedPrototype>()
    val concepts = mutableMapOf<String, GeneratedConcept>()

    var builtins: Map<String, TypeName> = emptyMap()
    var predefined: Map<String, TypeName> = emptyMap()

    private val extraSealedIntfs = mutableListOf<SealedIntf>()
    val allSubclassGetters = mutableListOf<String>()

    @GeneratedPrototypesDsl
    inner class Prototypes {
        fun prototype(
            name: String,
            block: GeneratedPrototypeBuilder.() -> Unit
        ) = this@GeneratedPrototypesBuilder.apply {
            val prototype = origPrototypes[name] ?: error("Prototype $name not found")
            prototypes[name] = GeneratedPrototypeBuilder(prototype).apply(block).build(prototype)
        }

        operator fun String.invoke(block: GeneratedPrototypeBuilder.() -> Unit) {
            prototype(this, block)
        }
    }

    inline fun prototypes(block: Prototypes.() -> Unit) = Prototypes().block()

    fun findConcept(name: String): Concept {
        return origConcepts[name] ?: error("Concept $name not found")
    }

    fun addConcept(name: String, concept: GeneratedConcept) {
        concepts[name] = concept
    }

    @GeneratedPrototypesDsl
    inner class Concepts {
        fun concept(name: String, block: GeneratedConceptBuilder.() -> Unit) {
            this@GeneratedPrototypesBuilder.addConcept(
                name, GeneratedConceptBuilder(
                    this@GeneratedPrototypesBuilder.findConcept(name)
                ).apply(block).build()
            )
        }

        operator fun String.invoke(block: GeneratedConceptBuilder.() -> Unit) {
            concept(this, block)
        }
    }

    inline fun concepts(block: Concepts.() -> Unit) = Concepts().block()


    fun extraSealedIntf(
        name: String,
        supertypes: List<String>,
        vararg subtypes: String, modify: (TypeSpec.Builder.() -> Unit)? = null
    ) {
        extraSealedIntfs.add(SealedIntf(name, supertypes, subtypes.toSet(), modify))
    }

    fun build(): GeneratedPrototypes {
        for (genPrototype in prototypes.values) {
            val prototype = genPrototype.inner
            if (prototype.parent != null) {
                check(prototype.parent in prototypes) {
                    "Parent of ${prototype.name} (${prototype.parent}) not defined"
                }
            }
        }
        for (value in extraSealedIntfs.flatMap { it.subtypes }) {
            check(value in prototypes || value in concepts) {
                "Extra sealed interface value $value not found"
            }
        }
        return GeneratedPrototypes(
            prototypes = prototypes,
            concepts = concepts,
            extraSealedIntfs = extraSealedIntfs,
            allSubclassGetters = allSubclassGetters,
            builtins = builtins,
            predefined = builtins + predefined
        )
    }
}

@GeneratedPrototypesDsl
class GeneratedPrototypeBuilder(val prototype: Prototype) {
    val properties = mutableMapOf<String, PropertyOptions>()

    var modify: (TypeSpec.Builder.() -> Unit)? = null

    fun tryAddProperty(
        name: String,
        block: PropertyOptions.() -> Unit = {}
    ): Boolean {
        if (name in properties) error("Property $name already defined")
        val property = prototype.properties.find { it.name == name } ?: return false
        properties[name] = PropertyOptions(property, null, null, null).apply(block)
        return true
    }

    fun property(
        name: String,
        block: PropertyOptions.() -> Unit
    ) {
        if (!tryAddProperty(name, block))
            error("Property $name not found on prototype ${prototype.name}")
    }

    operator fun String.invoke(block: PropertyOptions.() -> Unit) {
        property(this, block)
    }

    operator fun String.unaryPlus() {
        property(this) {}
    }

    fun build(prototype: Prototype): GeneratedPrototype = GeneratedPrototype(prototype, properties, modify)
}

@GeneratedPrototypesDsl
class GeneratedConceptBuilder(val concept: Concept) {
    var overrideType: Pair<TypeName, TypeSpec?>? = null
    fun overrideType(type: TypeName) {
        overrideType = type to null
    }

    var innerEnumName: String? = null
    var modify: (TypeSpec.Builder.() -> Unit)? = null
    private val properties: MutableMap<String, PropertyOptions> = mutableMapOf()

    var includeAllProperties: Boolean = true

    var isSealedIntf: Boolean = false

    fun property(
        name: String,
        block: PropertyOptions.() -> Unit
    ) {
        if (name in properties) error("Property $name already defined")
        val property = concept.properties?.find { it.name == name } ?: error("Property $name not found")
        properties[name] = PropertyOptions(property).apply(block)
    }

    operator fun String.invoke(block: PropertyOptions.() -> Unit) {
        property(this, block)
    }

    operator fun String.unaryPlus() {
        property(this) {}
    }

    private fun Property.getTypeValue(): String? {
        if (name != "type") return null
        return (type.innerType() as? LiteralType)?.value?.takeIf { it.isString }?.content
    }

    fun build(): GeneratedConcept {
        if (includeAllProperties)
            for (property in concept.properties.orEmpty()) {
                if (property.name !in properties) {
                    properties[property.name] = PropertyOptions(property)
                }
            }
        val typeProperty = properties["type"]
        val typeName = typeProperty?.inner?.getTypeValue()
        if (typeName != null) {
            properties.remove("type")
        }

        return GeneratedConcept(
            concept,
            overrideType = overrideType,
            innerEnumName = innerEnumName,
            properties,
            typeName,
            modify,
            isSealedIntf
        )
    }
}


fun getAllPrototypeSubclasses(
    prototypes: Map<String, Prototype>,
    baseName: String,
    includeBase: Boolean = true
): List<Prototype> {
    val isSubclass = mutableMapOf<String, Boolean>()
    isSubclass[baseName] = true
    fun isSubclass(prototype: Prototype): Boolean = isSubclass.getOrPut(prototype.name) {
        val parent = prototype.parent
        parent != null && isSubclass(prototypes[parent]!!)
    }
    if (!includeBase) isSubclass.remove(baseName)
    return prototypes.values.filter { isSubclass(it) }
}
