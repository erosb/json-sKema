package com.github.erosb.jsonsKema

data class FalseSchema(override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitFalseSchema(this)
}

data class FalseValidationFailure(
    override val schema: FalseSchema,
    override val instance: IJsonValue,
    val dynamicPath: JsonPointer
) : ValidationFailure("false schema always fails", schema, instance, Keyword.FALSE)

