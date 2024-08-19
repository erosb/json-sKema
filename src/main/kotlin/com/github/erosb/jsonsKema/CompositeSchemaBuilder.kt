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

abstract class SchemaBuilder {
    companion object {
        private fun type(typeValue: String): SchemaSupplier =
            { ptr ->
                TypeSchema(
                    JsonString(typeValue, callingSourceLocation(ptr + "type")),
                    callingSourceLocation(ptr + "type"),
                )
            }

        @JvmStatic
        fun typeString(): CompositeSchemaBuilder = CompositeSchemaBuilder(listOf(type("string")))

        @JvmStatic
        fun typeObject(): CompositeSchemaBuilder = CompositeSchemaBuilder(listOf(type("object")))

        @JvmStatic
        fun typeArray(): CompositeSchemaBuilder = CompositeSchemaBuilder(listOf(type("array")))

        @JvmStatic
        fun typeNumber(): CompositeSchemaBuilder = CompositeSchemaBuilder(listOf(type("number")))

        @JvmStatic
        fun typeInteger(): CompositeSchemaBuilder = CompositeSchemaBuilder(listOf(type("integer")))

        @JvmStatic
        fun typeBoolean(): CompositeSchemaBuilder = CompositeSchemaBuilder(listOf(type("boolean")))

        @JvmStatic
        fun typeNull(): CompositeSchemaBuilder = CompositeSchemaBuilder(listOf(type("null")))

        @JvmStatic
        fun empty(): CompositeSchemaBuilder = CompositeSchemaBuilder(listOf())

        @JvmStatic
        fun falseSchema(): SchemaBuilder = FalseSchemaBuilder()

        fun trueSchema(): SchemaBuilder = TrueSchemaBuilder()

        @JvmStatic
        fun ifSchema(ifSchema: SchemaBuilder) = SchemaBuilder.empty().ifSchema(ifSchema)
    }

    protected var ptr: JsonPointer = JsonPointer()

    open fun buildAt(parentPointer: JsonPointer): Schema {
        val origPtr = this.ptr
        this.ptr = parentPointer
        try {
            return build()
        } finally {
            this.ptr = origPtr
        }
    }

    abstract fun build(): Schema

    fun buildAt(loc: SourceLocation) = buildAt(loc.pointer)
}

class FalseSchemaBuilder(
    private val origLocation: SourceLocation = callingSourceLocation(JsonPointer()),
) : SchemaBuilder() {
    override fun build(): Schema = FalseSchema(origLocation.withPointer(ptr + Keyword.FALSE.value))
}

class TrueSchemaBuilder(
    private val origLocation: SourceLocation = callingSourceLocation(JsonPointer()),
) : SchemaBuilder() {
    override fun build(): Schema = TrueSchema(origLocation.withPointer(ptr + Keyword.TRUE.value))
}

class CompositeSchemaBuilder internal constructor(
    subschemas: List<SchemaSupplier>,
) : SchemaBuilder() {
    private val subschemas: MutableList<SchemaSupplier> = subschemas.toMutableList()
    private val propertySchemas = mutableMapOf<String, SchemaSupplier>()
    private val patternPropertySchemas = mutableMapOf<String, SchemaSupplier>()
    private var unevaluatedPropertiesSchema: SchemaSupplier? = null
    private var unevaluatedItemsSchema: SchemaSupplier? = null
    private var ifSchema: SchemaSupplier? = null
    private var thenSchema: SchemaSupplier? = null
    private var elseSchema: SchemaSupplier? = null
    private val regexFactory = JavaUtilRegexpFactory()

    fun minLength(minLength: Int): CompositeSchemaBuilder {
        val callingLocation = callingSourceLocation(JsonPointer())
        subschemas.add { ptr -> MinLengthSchema(minLength, callingLocation.withPointer(ptr + Keyword.MIN_LENGTH)) }
        return this
    }

    fun maxLength(maxLength: Int): CompositeSchemaBuilder {
        val callingLocation = callingSourceLocation(JsonPointer())
        subschemas.add { ptr -> MaxLengthSchema(maxLength, callingLocation.withPointer(ptr + Keyword.MAX_LENGTH)) }
        return this
    }

    override fun build(): Schema {
        val ifSchema = this.ifSchema
        if (ifSchema != null) {
            subschemas.add { loc -> IfThenElseSchema(ifSchema(ptr), thenSchema?.invoke(loc), elseSchema?.invoke(loc), callingSourceLocation(loc))}
        }
        return CompositeSchema(
            subschemas =
            subschemas
                .map { it(ptr) }
                .toSet(),
            location = callingSourceLocation(ptr),
            propertySchemas = propertySchemas.mapValues { it.value(ptr) },
            patternPropertySchemas =
            patternPropertySchemas
                .map {
                    regexFactory.createHandler(it.key) to it.value(ptr)
                }.toMap(),
            unevaluatedPropertiesSchema = unevaluatedPropertiesSchema?.invoke(ptr),
            unevaluatedItemsSchema = unevaluatedItemsSchema?.invoke(ptr),
        )
    }

    fun property(
        propertyName: String,
        schema: CompositeSchemaBuilder,
    ): CompositeSchemaBuilder {
        propertySchemas[propertyName] = { ptr ->
            schema.buildAt(ptr + Keyword.PROPERTIES.value + propertyName)
        }
        return this
    }

    fun minItems(minItems: Int): CompositeSchemaBuilder = appendSupplier(Keyword.MIN_ITEMS) { loc -> MinItemsSchema(minItems, loc) }

    fun maxItems(maxItems: Int): CompositeSchemaBuilder = appendSupplier(Keyword.MAX_ITEMS) { loc -> MaxItemsSchema(maxItems, loc) }

    private fun createSupplier(
        kw: Keyword,
        baseSchemaFn: (SourceLocation) -> Schema,
    ): SchemaSupplier {
        val callingLocation = callingSourceLocation(JsonPointer())
        return { ptr ->
            baseSchemaFn(callingLocation.withPointer(ptr + kw.value))
        }
    }

    private fun appendSupplier(
        kw: Keyword,
        baseSchemaFn: (SourceLocation) -> Schema,
    ): CompositeSchemaBuilder {
        subschemas.add(createSupplier(kw, baseSchemaFn))
        return this
    }

    fun items(itemsSchema: SchemaBuilder): CompositeSchemaBuilder =
        appendSupplier(Keyword.ITEMS) { loc ->
            ItemsSchema(itemsSchema.buildAt(loc), 0, loc)
        }

    fun contains(containedSchema: SchemaBuilder) =
        appendSupplier(Keyword.CONTAINS) { loc ->
            ContainsSchema(containedSchema.buildAt(loc), 1, null, loc)
        }

    fun minContains(
        minContains: Int,
        containedSchema: SchemaBuilder,
    ) = appendSupplier(Keyword.MIN_CONTAINS) { loc ->
        ContainsSchema(containedSchema.buildAt(loc), minContains, null, loc)
    }

    fun maxContains(
        maxContains: Int,
        containedSchema: SchemaBuilder,
    ) = appendSupplier(Keyword.MAX_CONTAINS) { loc ->
        ContainsSchema(containedSchema.buildAt(loc), 0, maxContains, loc)
    }

    fun uniqueItems() = appendSupplier(Keyword.UNIQUE_ITEMS) { loc -> UniqueItemsSchema(true, loc) }

    fun minProperties(minProperties: Int) = appendSupplier(Keyword.MIN_PROPERTIES) { loc -> MinPropertiesSchema(minProperties, loc) }

    fun maxProperties(maxProperties: Int) = appendSupplier(Keyword.MAX_PROPERTIES) { loc -> MaxPropertiesSchema(maxProperties, loc) }

    fun propertyNames(propertyNameSchema: SchemaBuilder) =
        appendSupplier(Keyword.PROPERTY_NAMES) { loc ->
            PropertyNamesSchema(
                propertyNameSchema.buildAt(loc),
                loc,
            )
        }

    fun required(vararg requiredProperties: String) =
        appendSupplier(Keyword.REQUIRED) { loc -> RequiredSchema(requiredProperties.toList(), loc) }

    fun dependentRequired(dependentRequired: Map<String, List<String>>) =
        appendSupplier(Keyword.DEPENDENT_REQUIRED) { loc ->
            DependentRequiredSchema(dependentRequired, loc)
        }

    fun readOnly(readOnly: Boolean) = if (readOnly) appendSupplier(Keyword.READ_ONLY) { loc -> ReadOnlySchema(loc) } else this

    fun writeOnly(writeOnly: Boolean) = if (writeOnly) appendSupplier(Keyword.WRITE_ONLY) { loc -> WriteOnlySchema(loc) } else this

    fun pattern(regexp: String) =
        appendSupplier(Keyword.PATTERN) { loc ->
            PatternSchema(regexFactory.createHandler(regexp), loc)
        }

    fun patternProperties(patternProps: Map<String, SchemaBuilder>): CompositeSchemaBuilder {
        patternProps.forEach { (pattern, builder) ->
            patternPropertySchemas[pattern] = { loc -> builder.buildAt(loc) }
        }
        return this
    }

    fun unevaluatedProperties(schema: SchemaBuilder): CompositeSchemaBuilder {
        unevaluatedPropertiesSchema =
            createSupplier(Keyword.UNEVALUATED_PROPERTIES) { loc ->
                UnevaluatedPropertiesSchema(schema.buildAt(loc), loc)
            }
        return this
    }

    fun unevaluatedItems(schema: SchemaBuilder): CompositeSchemaBuilder {
        unevaluatedItemsSchema =
            createSupplier(Keyword.UNEVALUATED_ITEMS) { loc ->
                UnevaluatedItemsSchema(schema.buildAt(loc), loc)
            }
        return this
    }

    fun ifSchema(ifSchema: SchemaBuilder): CompositeSchemaBuilder {
        this.ifSchema = createSupplier(Keyword.IF) { loc -> ifSchema.buildAt(loc) }
        return this
    }

    fun thenSchema(thenSchema: SchemaBuilder): CompositeSchemaBuilder {
        this.thenSchema = createSupplier(Keyword.THEN) { loc -> thenSchema.buildAt(loc) }
        return this
    }

    fun elseSchema(elseSchema: SchemaBuilder): CompositeSchemaBuilder {
        this.elseSchema = createSupplier(Keyword.ELSE) { loc -> elseSchema.buildAt(loc) }
        return this
    }

    fun minimum(minimum: Number) = appendSupplier(Keyword.MINIMUM) { loc -> MinimumSchema(minimum, loc) }

    fun maximum(minimum: Number) = appendSupplier(Keyword.MAXIMUM) { loc -> MaximumSchema(minimum, loc) }
}
