package com.github.erosb.jsonsKema

import java.net.URI

typealias SchemaSupplier = (JsonPointer) -> Schema

internal fun findFirstCodeOutsidePackage(pointer: JsonPointer): SourceLocation {
    val elem =
        Thread.currentThread().stackTrace.find {
            it.methodName != "getStackTrace" &&
                !it.className.startsWith("com.github.erosb.jsonsKema.")
        }
    return elem?.let {
        SourceLocation(
            pointer = pointer,
            documentSource = URI("classpath://" + elem.className),
            lineNumber = elem.lineNumber,
            position = 0,
        )
    } ?: UnknownSource
}

class SchemaBuilder private constructor(
    subschemas: List<SchemaSupplier>,
) {
    companion object {
        private fun type(typeValue: String): SchemaSupplier = { ptr ->
            TypeSchema(
                JsonString(typeValue, findFirstCodeOutsidePackage(ptr + "type")),
                findFirstCodeOutsidePackage(ptr + "type"),
            )
        }

        fun typeString(): SchemaBuilder = SchemaBuilder(listOf(type("string")))
        fun typeObject(): SchemaBuilder = SchemaBuilder(listOf(type("object")))
        fun typeArray(): SchemaBuilder  = SchemaBuilder(listOf(type("array")))
    }

    private val subschemas: MutableList<SchemaSupplier> = subschemas
        .toMutableList()
    private val propertySchemas = mutableMapOf<String, Schema>()
    private var ptr: JsonPointer = JsonPointer()

//    private fun addSubschema(schema: Schema) {
//        subschemas.add(schema)
//    }

    fun minLength(minLength: Int): SchemaBuilder {
        subschemas.add { ptr -> MinLengthSchema(minLength, findFirstCodeOutsidePackage(ptr + Keyword.MIN_LENGTH.value)) }
        return this
    }

    fun maxLength(maxLength: Int): SchemaBuilder {
        subschemas.add { ptr -> MaxLengthSchema(maxLength, findFirstCodeOutsidePackage(ptr + Keyword.MAX_LENGTH.value)) }
        return this
    }

    fun build(): Schema =
        CompositeSchema(
            subschemas = subschemas
                .map { it(ptr) }
                .toSet(),
            location = findFirstCodeOutsidePackage(ptr),
            propertySchemas = propertySchemas,
        )

    fun property(
        propertyName: String,
        schema: SchemaBuilder,
    ): SchemaBuilder {
        schema.ptr = ptr + "properties" + propertyName
        propertySchemas[propertyName] = schema.build()
        return this
    }
}
