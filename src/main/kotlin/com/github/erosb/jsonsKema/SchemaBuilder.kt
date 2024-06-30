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
    subschemas: List<Schema>,
) {
    companion object {
        private fun type(typeValue: String): TypeSchema =
            TypeSchema(
                JsonString(typeValue, findFirstCodeOutsidePackage(JsonPointer("type"))),
                findFirstCodeOutsidePackage(JsonPointer("type")),
            )

        fun typeString(): SchemaBuilder = SchemaBuilder(listOf(type("string")))

        fun typeObject(): SchemaBuilder = SchemaBuilder(listOf(type("object")))
    }

    private val subschemas: MutableList<Schema> = subschemas.toMutableList()
    private val propertySchemas = mutableMapOf<String, Schema>()
    private var ptr: MutableList<String> = mutableListOf()

    private fun addSubschema(schema: Schema) {
        subschemas.add(schema)
    }

    fun minLength(minLength: Int): SchemaBuilder {
        addSubschema(MinLengthSchema(minLength, findFirstCodeOutsidePackage(JsonPointer(Keyword.MIN_LENGTH.value))))
        return this
    }

    fun maxLength(maxLength: Int): SchemaBuilder {
        addSubschema(MaxLengthSchema(maxLength, findFirstCodeOutsidePackage(JsonPointer(Keyword.MAX_LENGTH.value))))
        return this
    }

    fun build(): Schema =
        CompositeSchema(
            subschemas = subschemas.toSet(),
            location = findFirstCodeOutsidePackage(JsonPointer(ptr)),
            propertySchemas = propertySchemas,
        )

    fun property(
        propertyName: String,
        schema: SchemaBuilder,
    ): SchemaBuilder {
        val newPtr = ptr.toMutableList()
        newPtr.add("properties")
        schema.ptr = newPtr
        propertySchemas[propertyName] = schema.build()
        return this
    }
}
