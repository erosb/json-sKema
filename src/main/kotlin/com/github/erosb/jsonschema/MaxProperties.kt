package com.github.erosb.jsonschema

data class MaxPropertiesSchema(val maxProperties: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMaxPropertiesSchema(this)
}
internal val maxPropertiesLoader: KeywordLoader = { containingObject: IJsonObj, keywordValue: IJsonValue, location: SourceLocation ->
    MaxPropertiesSchema(keywordValue.requireNumber().value, location)
}

data class MaxPropertiesValidationFailure(
    override val schema: MaxPropertiesSchema,
    override val instance: IJsonObj
) : ValidationFailure("expected maximum properties: ${schema.maxProperties}, found ${instance.properties.size}", schema, instance, Keyword.MIN_PROPERTIES)
