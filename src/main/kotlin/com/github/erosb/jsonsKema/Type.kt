package com.github.erosb.jsonsKema

data class TypeSchema(val type: IJsonString, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitTypeSchema(this)
}

data class MultiTypeSchema(val types: IJsonArray<*>, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMultiTypeSchema(this)
}

internal val typeLoader: KeywordLoader = { ctx ->
    val schema = ctx.keywordValue.maybeString { TypeSchema(it, ctx.location) }
        ?: ctx.keywordValue.maybeArray { MultiTypeSchema(it, ctx.location) }
    if (schema == null) {
        throw JsonTypingException("string or array", ctx.keywordValue.jsonTypeAsString(), ctx.location)
    }
    schema
}

data class TypeValidationFailure(
    val actualInstanceType: String,
    override val schema: TypeSchema,
    override val instance: IJsonValue,
    override val dynamicPath: JsonPointer
) : ValidationFailure("expected type: ${schema.type.value}, actual: $actualInstanceType", schema, instance, Keyword.TYPE)

data class MultiTypeValidationFailure(
    val actualInstanceType: String,
    override val schema: MultiTypeSchema,
    override val instance: IJsonValue,
    override val dynamicPath: JsonPointer
) : ValidationFailure(
    "expected type: one of ${schema.types.elements.joinToString { ", " }}, actual: $actualInstanceType",
    schema,
    instance,
    Keyword.TYPE
)
