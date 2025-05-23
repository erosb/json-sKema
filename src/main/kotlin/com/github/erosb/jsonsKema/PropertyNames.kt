package com.github.erosb.jsonsKema

data class PropertyNamesSchema(
    val propertyNamesSchema: Schema,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitPropertyNamesSchema(this)
    override fun subschemas(): Collection<Schema> = setOf(propertyNamesSchema)
}

internal val propertyNamesLoader: KeywordLoader = {ctx ->
        PropertyNamesSchema(ctx.subschemaLoader(ctx.keywordValue), ctx.location)
}

data class PropertyNamesValidationFailure(
    override val schema: PropertyNamesSchema,
    override val instance: IJsonObj,
    val causesByProperties: Map<IJsonString, ValidationFailure>,
    override val dynamicPath: DynamicPath
) : ValidationFailure(
    message = "",
    schema = schema,
    instance = instance,
    keyword = Keyword.PROPERTY_NAMES,
    causes = causesByProperties.values.toSet()
)
