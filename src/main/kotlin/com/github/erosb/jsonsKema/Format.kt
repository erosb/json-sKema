package com.github.erosb.jsonsKema

data class FormatSchema(
    val format: String,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitFormatSchema(this)
}

internal val formatLoader: KeywordLoader = { _, keywordValue, location ->
    FormatSchema(keywordValue.requireString().value, location)
}
data class FormatValidationFailure(
    override val schema: FormatSchema,
    override val instance: IJsonValue
) : ValidationFailure(
    message = "instance does not match format '${schema.format}'",
    keyword = Keyword.FORMAT,
    causes = setOf(),
    schema = schema,
    instance = instance
)
