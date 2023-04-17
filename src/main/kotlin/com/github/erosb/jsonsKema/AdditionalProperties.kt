package com.github.erosb.jsonsKema

internal val additionalPropertiesLoader: KeywordLoader = { ctx ->
    val keysInProperties = ctx.containingObject["properties"]?.requireObject()
        ?.properties?.keys?.map { it.value } ?: listOf()
    val patternPropertyKeys = ctx.containingObject["patternProperties"]
        ?.requireObject()?.properties?.keys
        ?.map { ctx.regexpFactory.createHandler(it.value) }
        ?: emptyList()
    AdditionalPropertiesSchema(ctx.loadSubschema(), keysInProperties, patternPropertyKeys, ctx.location)
}

data class AdditionalPropertiesSchema(
    val subschema: Schema,
    val keysInProperties: List<String>,
    val patternPropertyKeys: Collection<Regexp>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitAdditionalPropertiesSchema(this)
    override fun subschemas() = listOf(subschema)
}
