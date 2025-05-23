package com.github.erosb.jsonsKema

data class ExclusiveMaximumSchema(val maximum: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitExclusiveMaximumSchema(this)
}

internal val exclusiveMaximumLoader: KeywordLoader = { ctx ->
    ExclusiveMaximumSchema(ctx.keywordValue.requireNumber().value, ctx.location)
}

data class ExclusiveMaximumValidationFailure(
    override val schema: ExclusiveMaximumSchema,
    override val instance: IJsonNumber,
    override val dynamicPath: DynamicPath
) : ValidationFailure("${instance.value} is greater than or equal to maximum ${schema.maximum}", schema, instance, Keyword.EXCLUSIVE_MAXIMUM)
