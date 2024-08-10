package com.github.erosb.jsonsKema

import java.net.URI

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

        @JvmStatic
        fun typeString(): SchemaBuilder = SchemaBuilder(listOf(type("string")))

        @JvmStatic
        fun typeObject(): SchemaBuilder = SchemaBuilder(listOf(type("object")))

        @JvmStatic
        fun typeArray(): SchemaBuilder = SchemaBuilder(listOf(type("array")))

        @JvmStatic
        fun typeNumber(): SchemaBuilder = SchemaBuilder(listOf(type("number")))

        @JvmStatic
        fun typeInteger(): SchemaBuilder = SchemaBuilder(listOf(type("integer")))

        @JvmStatic
        fun typeBoolean(): SchemaBuilder = SchemaBuilder(listOf(type("boolean")))

        @JvmStatic
        fun typeNull(): SchemaBuilder = SchemaBuilder(listOf(type("null")))
    }

    private val subschemas: MutableList<SchemaSupplier> =
        subschemas
            .toMutableList()
    private val propertySchemas = mutableMapOf<String, SchemaSupplier>()
    private val patternPropertySchemas = mutableMapOf<String, SchemaSupplier>()
    private var ptr: JsonPointer = JsonPointer()
    private val regexFactory = JavaUtilRegexpFactory()

    fun minLength(minLength: Int): SchemaBuilder {
        val callingLocation = callingSourceLocation(JsonPointer())
        subschemas.add { ptr -> MinLengthSchema(minLength, callingLocation.withPointer(ptr + Keyword.MIN_LENGTH)) }
        return this
    }

    fun maxLength(maxLength: Int): SchemaBuilder {
        val callingLocation = callingSourceLocation(JsonPointer())
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
            patternPropertySchemas =
                patternPropertySchemas.map {
                    regexFactory.createHandler(it.key,) to it.value(ptr)
                }.toMap(),
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

    private fun buildAt(loc: SourceLocation) = buildAt(loc.pointer)

    fun property(
        propertyName: String,
        schema: SchemaBuilder,
    ): SchemaBuilder {
        propertySchemas[propertyName] = { ptr ->
            schema.buildAt(ptr + Keyword.PROPERTIES.value + propertyName)
        }
        return this
    }

    fun minItems(minItems: Int): SchemaBuilder = appendSupplier(Keyword.MIN_ITEMS) { loc -> MinItemsSchema(minItems, loc) }

    fun maxItems(maxItems: Int): SchemaBuilder = appendSupplier(Keyword.MAX_ITEMS) { loc -> MaxItemsSchema(maxItems, loc) }

    private fun createSupplier(
        kw: Keyword,
        baseSchemaFn: (SourceLocation) -> Schema,
    ): SchemaSupplier {
        val callingLocation = callingSourceLocation(JsonPointer())
        return { ptr ->
            baseSchemaFn(callingLocation.withPointer(ptr + kw.value))
        }
    }

    private fun appendSupplier(kw: Keyword, baseSchemaFn: (SourceLocation) -> Schema): SchemaBuilder {
        subschemas.add(createSupplier(kw, baseSchemaFn))
        return this
    }

    fun items(itemsSchema: SchemaBuilder): SchemaBuilder =
        appendSupplier(Keyword.ITEMS) { loc ->
            ItemsSchema(itemsSchema.buildAt(loc), 0, loc)
        }

    fun contains(containedSchema: SchemaBuilder) =
        appendSupplier(Keyword.CONTAINS) { loc ->
            ContainsSchema(containedSchema.buildAt(loc), 1, null, loc)
        }

    fun minContains(minContains: Int, containedSchema: SchemaBuilder) = appendSupplier(Keyword.MIN_CONTAINS) { loc ->
        ContainsSchema(containedSchema.buildAt(loc), minContains, null, loc)
    }

    fun maxContains(maxContains: Int, containedSchema: SchemaBuilder) = appendSupplier(Keyword.MAX_CONTAINS) { loc ->
        ContainsSchema(containedSchema.buildAt(loc), 0, maxContains, loc)
    }

    fun uniqueItems() = appendSupplier(Keyword.UNIQUE_ITEMS) { loc -> UniqueItemsSchema(true, loc) }

    fun minProperties(minProperties: Int) = appendSupplier(Keyword.MIN_PROPERTIES) { loc -> MinPropertiesSchema(minProperties, loc) }

    fun maxProperties(maxProperties: Int) = appendSupplier(Keyword.MAX_PROPERTIES) { loc -> MaxPropertiesSchema(maxProperties, loc) }

    fun propertyNames(propertyNameSchema: SchemaBuilder) = appendSupplier(Keyword.PROPERTY_NAMES) { loc -> PropertyNamesSchema(
        propertyNameSchema.buildAt(loc), loc
    )}


    fun required(vararg requiredProperties: String) = appendSupplier(Keyword.REQUIRED) { loc -> RequiredSchema(requiredProperties.toList(), loc) }

    fun dependentRequired(dependentRequired: Map<String, List<String>>) = appendSupplier(Keyword.DEPENDENT_REQUIRED) {
            loc -> DependentRequiredSchema(dependentRequired, loc)
    }

    fun readOnly(readOnly: Boolean) = if (readOnly) appendSupplier(Keyword.READ_ONLY) { loc -> ReadOnlySchema(loc) } else this

    fun writeOnly(writeOnly: Boolean) = if (writeOnly) appendSupplier(Keyword.WRITE_ONLY) { loc -> WriteOnlySchema(loc) } else this

    fun pattern(regexp: String) =
        appendSupplier(Keyword.PATTERN) { loc ->
            PatternSchema(regexFactory.createHandler(regexp), loc)
        }

    fun patternProperties(patternProps: Map<String, SchemaBuilder>): SchemaBuilder {
        patternProps.forEach { (pattern, builder) ->
            patternPropertySchemas[pattern] = { loc -> builder.buildAt(loc) }
        }
        return this
    }
}
