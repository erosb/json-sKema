package com.github.erosb.jsonsKema

data class MinPropertiesSchema(val minProperties: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMinPropertiesSchema(this)
}
internal val minPropertiesLoader: KeywordLoader = { containingObject: IJsonObj, keywordValue: IJsonValue, location: SourceLocation ->
    MinPropertiesSchema(keywordValue.requireNumber().value, location)
}

data class MinPropertiesValidationFailure(
    override val schema: MinPropertiesSchema,
    override val instance: IJsonObj
) : ValidationFailure("expected minimum properties: ${schema.minProperties}, found only ${instance.properties.size}", schema, instance, Keyword.MIN_PROPERTIES)
