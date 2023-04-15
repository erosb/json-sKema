package com.github.erosb.jsonsKema

internal val propertyNamesLoader: KeywordLoader = {ctx ->
        PropertyNamesSchema(ctx.subschemaLoader(ctx.keywordValue), ctx.location)
}

data class PropertyNamesValidationFailure(
    override val schema: PropertyNamesSchema,
    override val instance: IJsonObj,
    val causesByProperties: Map<IJsonString, ValidationFailure>
) : ValidationFailure(
    message = "",
    schema = schema,
    instance = instance,
    keyword = Keyword.PROPERTY_NAMES,
    causes = causesByProperties.values.toSet()
)
