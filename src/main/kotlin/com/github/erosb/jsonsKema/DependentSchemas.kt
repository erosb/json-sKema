package com.github.erosb.jsonsKema

data class DependentSchemasSchema(
    val dependentSchemas: Map<String, Schema>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitDependentSchemas(this)
}

internal val dependentSchemasLoader: KeywordLoader = { ctx ->
    DependentSchemasSchema(
        ctx.keywordValue.requireObject().properties
            .mapKeys { it.key.value }
            .mapValues { ctx.subschemaLoader(it.value) },
        ctx.location
    )
}

data class DependentSchemasValidationFailure(
    override val schema: DependentSchemasSchema,
    override val instance: IJsonValue,
    val causesByProperty: Map<String, ValidationFailure>,
    override val dynamicPath: JsonPointer
) : ValidationFailure(
    message = "some dependent subschemas did not match",
    schema = schema,
    instance = instance,
    keyword = Keyword.DEPENDENT_SCHEMAS,
    causes = causesByProperty.values.toSet()
)
