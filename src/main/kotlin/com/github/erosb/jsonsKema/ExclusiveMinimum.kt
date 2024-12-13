package com.github.erosb.jsonsKema

data class ExclusiveMinimumSchema(val minimum: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitExclusiveMinimumSchema(this)
}

internal val exclusiveMinimumLoader: KeywordLoader = { ctx ->
    ExclusiveMinimumSchema(ctx.keywordValue.requireNumber().value, ctx.location)
}

data class ExclusiveMinimumValidationFailure(
    override val schema: ExclusiveMinimumSchema,
    override val instance: IJsonNumber,
    override val dynamicPath: JsonPointer
) : ValidationFailure("${instance.value} is lower than or equal to minimum ${schema.minimum}", schema, instance, Keyword.EXCLUSIVE_MINIMUM)
