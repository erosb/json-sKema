package com.github.erosb.jsonsKema

data class ConstSchema(val constant: IJsonValue, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitConstSchema(this)
}

internal val constLoader: KeywordLoader = { ctx -> ConstSchema(ctx.keywordValue, ctx.location)}

data class ConstValidationFailure(
    override val schema: ConstSchema,
    override val instance: IJsonValue,
    override val dynamicPath: DynamicPath
) : ValidationFailure(
    "actual instance is not the same as expected constant value",
    schema,
    instance,
    Keyword.CONST
)
