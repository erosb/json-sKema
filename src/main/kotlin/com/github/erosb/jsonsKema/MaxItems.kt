package com.github.erosb.jsonsKema

data class MaxItemsSchema(val maxItems: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMaxItemsSchema(this)
}
internal val maxItemsLoader: KeywordLoader = { ctx ->
    MaxItemsSchema(ctx.keywordValue.requireNumber().value, ctx.location)
}

data class MaxItemsValidationFailure(
    override val schema: MaxItemsSchema,
    override val instance: IJsonArray<*>,
    override val dynamicPath: SourceLocation
) : ValidationFailure("expected maximum items: ${schema.maxItems}, found ${instance.length()}", schema, instance, Keyword.MAX_ITEMS)
