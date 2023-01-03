package com.github.erosb.jsonschema

data class MaxItemsSchema(val maxItems: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMaxItemsSchema(this)
}
internal val maxItemsLoader: KeywordLoader = { containingObject: IJsonObj, keywordValue: IJsonValue, location: SourceLocation ->
    MaxItemsSchema(keywordValue.requireNumber().value, location)
}

data class MaxItemsValidationFailure(
    override val schema: MaxItemsSchema,
    override val instance: IJsonArray<*>
) : ValidationFailure("expected maximum items: ${schema.maxItems}, found ${instance.length()}", schema, instance, Keyword.MAX_ITEMS)
