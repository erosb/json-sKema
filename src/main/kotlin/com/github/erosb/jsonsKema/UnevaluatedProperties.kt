package com.github.erosb.jsonsKema

data class UnevaluatedPropertiesSchema(
    val unevaluatedPropertiesSchema: Schema,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitUnevaluatedPropertiesSchema(this)

    override fun subschemas() = listOf(unevaluatedPropertiesSchema)
}

data class UnevaluatedPropertiesValidationFailure(
    val propertyFailures: Map<String, ValidationFailure>,
    override val schema: UnevaluatedPropertiesSchema,
    override val instance: IJsonObj,
    override val dynamicPath: SourceLocation
) : ValidationFailure(
    "object properties ${propertyFailures.keys.joinToString(", ")} failed to validate against \"unevaluatedProperties\" subschema",
    schema,
    instance,
    Keyword.UNEVALUATED_PROPERTIES,
    propertyFailures.values.toSet()
)
