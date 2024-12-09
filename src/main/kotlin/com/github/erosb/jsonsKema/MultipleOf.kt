package com.github.erosb.jsonsKema

data class MultipleOfSchema(val denominator: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMultipleOfSchema(this)
}

internal val multipleOfLoader: KeywordLoader = { ctx ->
    MultipleOfSchema(ctx.keywordValue.requireNumber().value, ctx.location)
}

data class MultipleOfValidationFailure(
    override val schema: MultipleOfSchema,
    override val instance: IJsonNumber,
    val dynamicPath: JsonPointer
) : ValidationFailure("${instance.value} is not a multiple of ${schema.denominator}", schema, instance, Keyword.MULTIPLE_OF)
