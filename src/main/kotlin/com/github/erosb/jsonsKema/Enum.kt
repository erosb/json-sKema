package com.github.erosb.jsonsKema

class EnumSchema(val potentialValues: Collection<IJsonValue>, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitEnumSchema(this)
}

internal val enumLoader: KeywordLoader = { ctx ->
    EnumSchema(ctx.keywordValue.requireArray().elements, ctx.location)
}

class EnumValidationFailure(
    override val schema: EnumSchema,
    override val instance: IJsonValue
) : ValidationFailure("the instance is not equal to any enum values", schema, instance, Keyword.ENUM)
