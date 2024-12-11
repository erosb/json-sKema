package com.github.erosb.jsonsKema

data class PrefixItemsValidationFailure(
    val itemFailures: Map<Int, ValidationFailure>,
    override val schema: PrefixItemsSchema,
    override val instance: IJsonArray<*>,
    val dynamicPath: JsonPointer
) : ValidationFailure(
    "array items ${itemFailures.keys.joinToString(", ")} failed to validate against \"prefixItems\" subschema",
    schema,
    instance,
    Keyword.PREFIX_ITEMS,
    itemFailures.values.toSet()
)

internal val prefixItemsLoader: KeywordLoader = { ctx ->
    PrefixItemsSchema(ctx.keywordValue.requireArray().elements.map { ctx.subschemaLoader(it) }, ctx.location)
}

data class PrefixItemsSchema(val prefixSchemas: List<Schema>, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitPrefixItemsSchema(this)
    override fun subschemas(): Collection<Schema> = prefixSchemas
}
