package com.github.erosb.jsonsKema

data class RequiredSchema(val requiredProperties: List<String>, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitRequiredSchema(this)
}

internal val requiredLoader: KeywordLoader = { ctx ->
    RequiredSchema(
        ctx.keywordValue.requireArray().elements.map { it.requireString().value },
        ctx.location
    )
}

data class RequiredValidationFailure(
    val missingProperties: List<String>,
    override val schema: RequiredSchema,
    override val instance: IJsonObj
) : ValidationFailure(
    "required properties are missing: " + missingProperties.joinToString(),
    schema,
    instance,
    Keyword.REQUIRED
)
