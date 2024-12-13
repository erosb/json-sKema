package com.github.erosb.jsonsKema

data class MaximumSchema(val maximum: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMaximumSchema(this)
}

internal val maximumLoader: KeywordLoader = { ctx ->
    MaximumSchema(ctx.keywordValue.requireNumber().value, ctx.location)
}

data class MaximumValidationFailure(
    override val schema: MaximumSchema,
    override val instance: IJsonNumber,
    override val dynamicPath: JsonPointer
) : ValidationFailure("${instance.value} is greater than maximum ${schema.maximum}", schema, instance, Keyword.MAXIMUM)
