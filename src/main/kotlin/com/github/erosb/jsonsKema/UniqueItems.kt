package com.github.erosb.jsonsKema

data class UniqueItemsSchema(val unique: Boolean, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitUniqueItemsSchema(this)
}

internal val uniqueItemsLoader: KeywordLoader = { ctx ->
    UniqueItemsSchema(ctx.keywordValue.requireBoolean().value, ctx.location)
}

data class UniqueItemsValidationFailure(
    val arrayPositions: List<Int>,
    override val schema: UniqueItemsSchema,
    override val instance: IJsonArray<*>,
    override val dynamicPath: DynamicPath
) : ValidationFailure("the same array element occurs at positions " + arrayPositions.joinToString(", "), schema, instance, Keyword.UNIQUE_ITEMS)
