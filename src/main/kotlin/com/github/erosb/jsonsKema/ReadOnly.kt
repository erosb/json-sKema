package com.github.erosb.jsonsKema

data class ReadOnlySchema(
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitReadOnlySchema(this)
}

internal val readOnlyLoader: KeywordLoader = {
    if (it.keywordValue.requireBoolean().value)
        ReadOnlySchema(it.location)
    else
        null
}

data class ReadOnlyValidationFailure(
    override val schema: Schema,
    override val instance: IJsonValue,
) : ValidationFailure(
    message = "read-only property \"${instance.location.pointer.segments.last()}\" should not be present in write context",
    schema = schema,
    instance = instance,
    keyword = Keyword.READ_ONLY,
    causes = setOf()
)
