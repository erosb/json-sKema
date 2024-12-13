package com.github.erosb.jsonsKema

data class UnevaluatedItemsSchema(
    val unevaluatedItemsSchema: Schema,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitUnevaluatedItemsSchema(this)
    override fun subschemas() = listOf(unevaluatedItemsSchema)
}

data class UnevaluatedItemsValidationFailure(
    val itemFailures: Map<Int, ValidationFailure>,
    override val schema: UnevaluatedItemsSchema,
    override val instance: IJsonArray<*>,
    override val dynamicPath: JsonPointer
) : ValidationFailure(
    "array items ${itemFailures.keys.joinToString(", ")} failed to validate against \"unevaluatedItems\" subschema",
    schema,
    instance,
    Keyword.UNEVALUATED_ITEMS,
    itemFailures.values.toSet()
)

