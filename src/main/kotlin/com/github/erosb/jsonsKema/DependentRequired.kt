package com.github.erosb.jsonsKema

data class DependentRequiredSchema(val dependentRequired: Map<String, List<String>>, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitDependentRequiredSchema(this)
}

internal val dependentRequiredLoader: KeywordLoader = { ctx ->
    val dependentRequired = ctx.keywordValue.requireObject().properties
        .map { it.key.value to it.value.requireArray().elements.map { it.requireString().value } }
        .toMap()
    DependentRequiredSchema(dependentRequired, ctx.location)
}

data class DependentRequiredValidationFailure(
    val presentKey: String,
    val missingKeys: Set<String>,
    override val schema: DependentRequiredSchema,
    override val instance: IJsonObj,
    override val dynamicPath: JsonPointer
) : ValidationFailure("property $presentKey is present in the object but the following properties are missing: ${missingKeys.joinToString(", ")}", schema, instance, Keyword.DEPENDENT_REQUIRED)
