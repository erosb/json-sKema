package com.github.erosb.jsonsKema

data class ContainsSchema(
    val containedSchema: Schema,
    val minContains: Number = 1,
    val maxContains: Number?,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitContainsSchema(this)
    override fun subschemas(): Collection<Schema> = listOf(containedSchema)
}

internal val containsLoader: KeywordLoader = { ctx ->
    val minContains = ctx.containingObject[Keyword.MIN_CONTAINS.value]?.maybeNumber { it.value } ?: 1
    val maxContains = ctx.containingObject[Keyword.MAX_CONTAINS.value]?.maybeNumber { it.value }
    ContainsSchema(ctx.subschemaLoader(ctx.keywordValue), minContains, maxContains, ctx.location)
}

data class ContainsValidationFailure(
    override val message: String,
    override val schema: ContainsSchema,
    override val instance: IJsonArray<*>,
    override val dynamicPath: DynamicPath
) : ValidationFailure(
    message,
    schema,
    instance,
    Keyword.CONTAINS
)
