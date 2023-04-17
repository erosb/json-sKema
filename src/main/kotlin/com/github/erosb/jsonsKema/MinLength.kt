package com.github.erosb.jsonsKema

data class MinLengthSchema(val minLength: Int, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitMinLengthSchema(this)
}

internal val minLengthLoader: KeywordLoader = { ctx ->
    MinLengthSchema(ctx.keywordValue.requireInt(), ctx.location)
}


data class MinLengthValidationFailure(
    override val schema: MinLengthSchema,
    override val instance: IJsonString
) : ValidationFailure(
    "actual string length ${instance.value.length} is lower than minLength ${schema.minLength}",
    schema,
    instance,
    Keyword.MIN_LENGTH
)
