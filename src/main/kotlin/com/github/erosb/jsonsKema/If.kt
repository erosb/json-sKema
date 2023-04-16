package com.github.erosb.jsonsKema

internal val ifThenElseLoader: KeywordLoader = {ctx ->
    val ifSchema = ctx.subschemaLoader(ctx.keywordValue)
    val thenSchema = ctx.containingObject[Keyword.THEN.value]?.let { ctx.subschemaLoader(it) }
    val elseSchema = ctx.containingObject[Keyword.ELSE.value]?.let { ctx.subschemaLoader(it) }
    IfThenElseSchema(ifSchema, thenSchema, elseSchema, ctx.location)
}

data class IfThenElseSchema(
    val ifSchema: Schema,
    val thenSchema: Schema?,
    val elseSchema: Schema?,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitIfThenElseSchema(this)
    override fun subschemas() = listOfNotNull(ifSchema, thenSchema, elseSchema)
}
