package com.github.erosb.jsonsKema

data class NotSchema(val negatedSchema: Schema, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitNotSchema(this)
    override fun subschemas(): Collection<Schema> = listOf(negatedSchema)
}

internal val notSchemaLoader: KeywordLoader = { ctx ->
    NotSchema(ctx.subschemaLoader(ctx.keywordValue), ctx.location)
}

data class NotValidationFailure(
    override val schema: Schema,
    override val instance: IJsonValue,
    val dynamicPath: JsonPointer
) : ValidationFailure("negated subschema did not fail", schema, instance, Keyword.NOT)
