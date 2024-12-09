package com.github.erosb.jsonsKema

data class MaxPropertiesSchema(val maxProperties: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMaxPropertiesSchema(this)
}
internal val maxPropertiesLoader: KeywordLoader = { ctx ->
    MaxPropertiesSchema(ctx.keywordValue.requireNumber().value, ctx.location)
}

data class MaxPropertiesValidationFailure(
    override val schema: MaxPropertiesSchema,
    override val instance: IJsonObj,
    val dynamicPath: JsonPointer
) : ValidationFailure("expected maximum properties: ${schema.maxProperties}, found ${instance.properties.size}", schema, instance, Keyword.MIN_PROPERTIES)
