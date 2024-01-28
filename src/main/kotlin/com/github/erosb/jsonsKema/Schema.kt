package com.github.erosb.jsonsKema

abstract class Schema(open val location: SourceLocation) {
    abstract fun <P> accept(visitor: SchemaVisitor<P>): P?
    open fun subschemas(): Collection<Schema> = emptyList()
}

data class CompositeSchema(
    val subschemas: Set<Schema>,
    override val location: SourceLocation = UnknownSource,
    val id: IJsonString? = null,
    val title: IJsonString? = null,
    val description: IJsonString? = null,
    val deprecated: IJsonBoolean? = null,
    val default: IJsonValue? = null,
    val dynamicRef: DynamicReference? = null,
    val dynamicAnchor: String? = null,
    val propertySchemas: Map<String, Schema> = emptyMap(),
    val patternPropertySchemas: Map<Regexp, Schema> = emptyMap(),
    val unevaluatedItemsSchema: Schema? = null,
    val unevaluatedPropertiesSchema: Schema? = null,
    val unprocessedProperties: Map<IJsonString, IJsonValue> = emptyMap(),
    val vocabulary: List<String> = emptyList()
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.internallyVisitCompositeSchema(this)
    override fun subschemas() = subschemas
}

data class ReferenceSchema(var referredSchema: Schema?, val ref: String, override val location: SourceLocation) :
    Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitReferenceSchema(this)
    override fun subschemas() = referredSchema?.let { listOf(it) } ?: emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReferenceSchema) return false

        if (location != other.location) return false

        return referredSchema === other.referredSchema
    }

    override fun hashCode(): Int {
        return location.hashCode()
    }

    override fun toString(): String {
        return "{\"\$ref\": \"${ref}\", \"resolved\":\"${referredSchema !== null}\"}"
    }
}

data class DynamicRefSchema(var referredSchema: Schema?, val dynamicRef: String, override val location: SourceLocation) :
    Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitDynamicRefSchema(this)
    override fun subschemas() = referredSchema?.let { listOf(it) } ?: emptyList()
}

data class DynamicReference(val ref: String, var fallbackReferredSchema: ReferenceSchema? = null)
