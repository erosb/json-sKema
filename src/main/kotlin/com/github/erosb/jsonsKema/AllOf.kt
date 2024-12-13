package com.github.erosb.jsonsKema

data class AllOfSchema(
    val subschemas: List<Schema>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitAllOfSchema(this)
    override fun subschemas(): Collection<Schema> = subschemas
}

internal val allOfLoader: KeywordLoader = { ctx ->
    AllOfSchema(arrayToSubschemaList(ctx.keywordValue.requireArray(), ctx.subschemaLoader), ctx.location)
}

data class AllOfValidationFailure(
    override val schema: AllOfSchema,
    override val instance: IJsonValue,
    override val causes: Set<ValidationFailure>,
    override val dynamicPath: JsonPointer
) : ValidationFailure(
    message = "${causes.size} subschemas out of ${schema.subschemas.size} failed to validate",
    schema = schema,
    instance = instance,
    causes = causes,
    keyword = Keyword.ALL_OF
)
