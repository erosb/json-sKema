package com.github.erosb.jsonsKema

data class ItemsSchema(val itemsSchema: Schema, val prefixItemCount: Int, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitItemsSchema(this)
    override fun subschemas(): Collection<Schema> = listOf(itemsSchema)
}

internal val itemsSchemaLoader: KeywordLoader = { ctx ->
    ItemsSchema(
        ctx.subschemaLoader(ctx.keywordValue),
        ctx.containingObject[Keyword.PREFIX_ITEMS.value]?.maybeArray { it.length() } ?: 0,
        ctx.location
    )
}

data class ItemsValidationFailure(
    val itemFailures: Map<Int, ValidationFailure>,
    override val schema: ItemsSchema,
    override val instance: IJsonArray<*>,
    override val dynamicPath: JsonPointer
) : ValidationFailure(
    "array items ${itemFailures.keys.joinToString(", ")} failed to validate against \"items\" subschema",
    schema,
    instance,
    Keyword.ITEMS,
    itemFailures.values.toSet()
)

