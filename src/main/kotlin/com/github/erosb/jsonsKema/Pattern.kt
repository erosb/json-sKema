package com.github.erosb.jsonsKema

data class PatternSchema(
    val pattern: Regexp,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitPatternSchema(this)
}

internal val patternLoader: KeywordLoader = { ctx ->
    PatternSchema(ctx.regexpFactory.createHandler(ctx.keywordValue.requireString().value), ctx.location)
}

data class PatternValidationFailure(
    override val schema: PatternSchema,
    override val instance: IJsonValue,
    override val dynamicPath: DynamicPath
) : ValidationFailure(
    message = "instance value did not match pattern ${schema.pattern}",
    schema = schema,
    instance = instance,
    keyword = Keyword.PATTERN
)

