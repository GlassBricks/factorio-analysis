package glassbricks.factorio.prototypecodegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec

class Generator(val apiDocs: PrototypeApiDocs) {

    private val file: FileSpec.Builder = FileSpec.builder("glassbricks.factorio.prototypes", "Prototypes")

    private fun generatePrototype(prototype: Prototype): TypeSpec {
        val type = TypeSpec.classBuilder(prototype.name).apply {
            // todo
        }
        return type.build()
    }

    private class PrototypeData(
        val prototype: Prototype,
    ) {

    }

    fun generate(): FileSpec {
        for (prototype in apiDocs.prototypes
            .sortedBy { it.order }
        ) {
            file.addType(generatePrototype(prototype))
        }

        return file.build()
    }


}