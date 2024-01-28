package com.github.erosb.jsonsKema

data class WriteOnlySchema(
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitWriteOnlySchema(this)
}

internal val writeOnlyLoader: KeywordLoader = {
    if (it.keywordValue.requireBoolean().value)
        WriteOnlySchema(it.location)
    else
        null
}

data class WriteOnlyValidationFailure(
    override val schema: Schema,
    override val instance: IJsonValue,
) : ValidationFailure(
    message = "write-only property \"${instance.location.pointer.segments.last()}\" should not be present in read context",
    schema = schema,
    instance = instance,
    keyword = Keyword.WRITE_ONLY,
    causes = setOf()
)
