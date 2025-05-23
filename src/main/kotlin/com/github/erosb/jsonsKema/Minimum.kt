package com.github.erosb.jsonsKema

data class MinimumValidationFailure(
    override val schema: MinimumSchema,
    override val instance: IJsonNumber,
    override val dynamicPath: DynamicPath
) : ValidationFailure("${instance.value} is lower than minimum ${schema.minimum}", schema, instance, Keyword.MINIMUM)

internal val minimumLoader:KeywordLoader = { ctx ->
    MinimumSchema(ctx.keywordValue.requireNumber().value, ctx.location)
}

data class MinimumSchema(val minimum: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMinimumSchema(this)
}
