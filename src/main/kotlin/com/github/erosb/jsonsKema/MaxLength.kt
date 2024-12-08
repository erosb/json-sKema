package com.github.erosb.jsonsKema

data class MaxLengthSchema(val maxLength: Int, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitMaxLengthSchema(this)
}

internal val maxLengthLoader: KeywordLoader = { ctx ->
    MaxLengthSchema(ctx.keywordValue.requireInt(), ctx.location)
}

data class MaxLengthValidationFailure(
    override val schema: MaxLengthSchema,
    override val instance: IJsonString,
    val dynamicPath: JsonPointer
) : ValidationFailure(
    "actual string length ${instance.value.length} exceeds maxLength ${schema.maxLength}",
    schema,
    instance,
    Keyword.MAX_LENGTH
)
