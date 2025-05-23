package com.github.erosb.jsonsKema

data class OneOfSchema(
    val subschemas: List<Schema>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitOneOfSchema(this)
    override fun subschemas(): Collection<Schema> = subschemas
}

internal val oneOfLoader: KeywordLoader = { ctx ->
    OneOfSchema(arrayToSubschemaList(ctx.keywordValue.requireArray(), ctx.subschemaLoader), ctx.location)
}

data class OneOfValidationFailure(
    override val schema: OneOfSchema,
    override val instance: IJsonValue,
    override val causes: Set<ValidationFailure>,
    override val dynamicPath: DynamicPath
) : ValidationFailure(
    message = "expected 1 subschema to match out of ${schema.subschemas.size}, ${schema.subschemas.size - causes.size} matched",
    schema = schema,
    instance = instance,
    causes = causes,
    keyword = Keyword.ONE_OF
)
