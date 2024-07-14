package com.github.erosb.jsonsKema

import java.net.URI
import java.security.Key

typealias SchemaSupplier = (JsonPointer) -> Schema

internal fun callingSourceLocation(pointer: JsonPointer): SourceLocation {
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
        private fun type(typeValue: String): SchemaSupplier =
            { ptr ->
                TypeSchema(
                    JsonString(typeValue, callingSourceLocation(ptr + "type")),
                    callingSourceLocation(ptr + "type"),
                )
            }

        fun typeString(): SchemaBuilder = SchemaBuilder(listOf(type("string")))

        fun typeObject(): SchemaBuilder = SchemaBuilder(listOf(type("object")))

        fun typeArray(): SchemaBuilder = SchemaBuilder(listOf(type("array")))

        fun typeNumber(): SchemaBuilder = SchemaBuilder(listOf(type("number")))

        fun typeInteger(): SchemaBuilder = SchemaBuilder(listOf(type("integer")))

        fun typeBoolean(): SchemaBuilder = SchemaBuilder(listOf(type("boolean")))

        fun typeNull(): SchemaBuilder = SchemaBuilder(listOf(type("null")))
    }

    private val subschemas: MutableList<SchemaSupplier> =
        subschemas
            .toMutableList()
    private val propertySchemas = mutableMapOf<String, SchemaSupplier>()
    private var ptr: JsonPointer = JsonPointer()

    fun minLength(minLength: Int): SchemaBuilder {
        val callingLocation  = callingSourceLocation(JsonPointer())
        subschemas.add { ptr -> MinLengthSchema(minLength, callingLocation.withPointer(ptr + Keyword.MIN_LENGTH)) }
        return this
    }

    fun maxLength(maxLength: Int): SchemaBuilder {
        val callingLocation  = callingSourceLocation(JsonPointer())
        subschemas.add { ptr -> MaxLengthSchema(maxLength, callingLocation.withPointer(ptr + Keyword.MAX_LENGTH)) }
        return this
    }

    fun build(): Schema =
        CompositeSchema(
            subschemas =
                subschemas
                    .map { it(ptr) }
                    .toSet(),
            location = callingSourceLocation(ptr),
            propertySchemas = propertySchemas.mapValues { it.value(ptr) },
        )

    private fun buildAt(parentPointer: JsonPointer): Schema {
        val origPtr = this.ptr
        this.ptr = parentPointer
        try {
            return build()
        } finally {
            this.ptr = origPtr
        }
    }

    fun property(
        propertyName: String,
        schema: SchemaBuilder,
    ): SchemaBuilder {
        propertySchemas[propertyName] = { ptr ->
            schema.buildAt(ptr + Keyword.PROPERTIES.value + propertyName)
        }
        return this
    }

    fun minItems(minItems: Int): SchemaBuilder {
        val callingLocation  = callingSourceLocation(JsonPointer())
        subschemas.add { ptr -> MinItemsSchema(minItems, callingLocation.withPointer(ptr + Keyword.MIN_ITEMS)) }
        return this
    }

    fun maxItems(maxItems: Int): SchemaBuilder {
        val callingLocation  = callingSourceLocation(JsonPointer())
        subschemas.add { ptr -> MaxItemsSchema(maxItems, callingLocation.withPointer(ptr + Keyword.MAX_ITEMS)) }
        return this
    }

    fun items(itemsSchema: SchemaBuilder): SchemaBuilder {
        subschemas.add { ptr ->
            ItemsSchema(
                itemsSchema.buildAt(ptr + Keyword.ITEMS.value),
                0,
                callingSourceLocation(ptr + Keyword.ITEMS.value),
            )
        }
        return this
    }

    fun contains(containedSchema: SchemaBuilder): SchemaBuilder {
        subschemas.add { ptr ->
            ContainsSchema(
                containedSchema.buildAt(ptr + Keyword.CONTAINS.value),
                1,
                null,
                callingSourceLocation(ptr + Keyword.CONTAINS.value),
            )
        }
        return this
    }
}
