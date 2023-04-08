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
    val readOnly: IJsonBoolean? = null,
    val writeOnly: IJsonBoolean? = null,
    val default: IJsonValue? = null,
    val dynamicRef: DynamicReference? = null,
    val dynamicAnchor: String? = null,
    val propertySchemas: Map<String, Schema> = emptyMap(),
    val patternPropertySchemas: Map<Regexp, Schema> = emptyMap(),
    val unevaluatedItemsSchema: Schema? = null,
    val unevaluatedPropertiesSchema: Schema? = null
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.internallyVisitCompositeSchema(this)
    override fun subschemas() = subschemas
}

data class AllOfSchema(
    val subschemas: List<Schema>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitAllOfSchema(this)
    override fun subschemas(): Collection<Schema> = subschemas
}

data class AnyOfSchema(
    val subschemas: List<Schema>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitAnyOfSchema(this)
    override fun subschemas(): Collection<Schema> = subschemas
}

data class OneOfSchema(
    val subschemas: List<Schema>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitOneOfSchema(this)
    override fun subschemas(): Collection<Schema> = subschemas
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

data class TrueSchema(override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitTrueSchema(this)
}

data class FalseSchema(override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitFalseSchema(this)
}

data class MinLengthSchema(val minLength: Int, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitMinLengthSchema(this)
}

data class MaxLengthSchema(val maxLength: Int, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitMaxLengthSchema(this)
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

data class ConstSchema(val constant: IJsonValue, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitConstSchema(this)
}

data class TypeSchema(val type: IJsonString, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitTypeSchema(this)
}

data class MultiTypeSchema(val types: IJsonArray<*>, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMultiTypeSchema(this)
}

data class NotSchema(val negatedSchema: Schema, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitNotSchema(this)
    override fun subschemas(): Collection<Schema> = listOf(negatedSchema)
}

data class RequiredSchema(val requiredProperties: List<String>, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitRequiredSchema(this)
}

data class MaximumSchema(val maximum: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMaximumSchema(this)
}

data class MinimumSchema(val minimum: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMinimumSchema(this)
}

data class ExclusiveMaximumSchema(val maximum: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitExclusiveMaximumSchema(this)
}

data class ExclusiveMinimumSchema(val minimum: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitExclusiveMinimumSchema(this)
}

data class MultipleOfSchema(val denominator: Number, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitMultipleOfSchema(this)
}

data class UniqueItemsSchema(val unique: Boolean, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitUniqueItemsSchema(this)
}

data class ItemsSchema(val itemsSchema: Schema, val prefixItemCount: Int, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitItemsSchema(this)
    override fun subschemas(): Collection<Schema> = listOf(itemsSchema)
}

data class PrefixItemsSchema(val prefixSchemas: List<Schema>, override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitPrefixItemsSchema(this)
    override fun subschemas(): Collection<Schema> = prefixSchemas
}

data class ContainsSchema(
    val containedSchema: Schema,
    val minContains: Number = 1,
    val maxContains: Number?,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitContainsSchema(this)
    override fun subschemas(): Collection<Schema> = listOf(containedSchema)
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

data class DependentSchemasSchema(
    val dependentSchemas: Map<String, Schema>,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitDependentSchemas(this)
}

data class UnevaluatedItemsSchema(
    val unevaluatedItemsSchema: Schema,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitUnevaluatedItemsSchema(this)
    override fun subschemas() = listOf(unevaluatedItemsSchema)
}

data class UnevaluatedPropertiesSchema(
    val unevaluatedPropertiesSchema: Schema,
    override val location: SourceLocation
) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>): P? = visitor.visitUnevaluatedPropertiesSchema(this)

    override fun subschemas() = listOf(unevaluatedPropertiesSchema)
}
