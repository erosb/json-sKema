package com.github.erosb.jsonsKema


data class AnyOfSchema(
    val subschemas: List<Schema>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitAnyOfSchema(this)
    override fun subschemas(): Collection<Schema> = subschemas
}

internal val anyOfLoader: KeywordLoader = { ctx ->
    AnyOfSchema(arrayToSubschemaList(ctx.keywordValue.requireArray(), ctx.subschemaLoader), ctx.location)
}

data class AnyOfValidationFailure(
    override val schema: AnyOfSchema,
    override val instance: IJsonValue,
    override val causes: Set<ValidationFailure>,
    val dynamicPath: JsonPointer
) : ValidationFailure(
    message = "no subschema out of ${schema.subschemas.size} matched",
    schema = schema,
    instance = instance,
    causes = causes,
    keyword = Keyword.ANY_OF
)
