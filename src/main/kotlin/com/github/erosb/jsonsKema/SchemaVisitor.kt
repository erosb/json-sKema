package com.github.erosb.jsonsKema

import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.util.Objects

abstract class SchemaVisitor<P> {

    private val anchors: MutableMap<String, CompositeSchema> = mutableMapOf()

    private val dynamicScope = mutableListOf<CompositeSchema>()

    private fun findSubschemaByDynamicAnchor(scope: CompositeSchema, lookupValue: String): Schema? {
        if (scope.dynamicAnchor == lookupValue) {
            return scope
        }
        return scope.subschemas().stream()
            .filter { it is CompositeSchema}
            .map { it as CompositeSchema }
            .map  { scope -> findSubschemaByDynamicAnchor(scope, lookupValue) }
            .filter (Objects::nonNull)
            .findAny()
            .orElse(null)
    }

    internal fun internallyVisitCompositeSchema(schema: CompositeSchema): P? {
        dynamicScope.add(schema)
        try {
            val dynamicRef = schema.dynamicRef
            if (dynamicRef != null) {
                val anchorName = dynamicRef.ref.substring(dynamicRef.ref.indexOf("#") + 1)
                var referred = dynamicScope.stream()
                    .map  { scope -> findSubschemaByDynamicAnchor(scope, anchorName) }
                    .filter (Objects::nonNull)
                    .findAny()
                    .orElse(null)
                if (referred === null) {
                    if (dynamicRef.fallbackReferredSchema == null) {
                        TODO("not implemented (no matching dynamicAnchor for dynamicRef $dynamicRef")
                    } else {
                        referred = dynamicRef.fallbackReferredSchema!!.referredSchema
                    }
                }
                val referredProduct = referred?.accept(this)
                val selfProduct = visitCompositeSchema(schema)
                return accumulate(schema, referredProduct, selfProduct)
            } else {
                return visitCompositeSchema(schema)
            }
        } finally {
            val popped = dynamicScope.removeLast()
            if (popped !== schema) {
                throw IllegalStateException()
            }
        }
    }

    open fun visitCompositeSchema(schema: CompositeSchema): P? {
        val subschemaProduct = visitChildren(schema)
        val propSchemaProduct: P? = if (schema.propertySchemas.isEmpty()) {
            null
        } else schema.propertySchemas
            .map { visitPropertySchema(it.key, it.value) }
            .reduce { a, b -> accumulate(schema, a, b) }
        var result = accumulate(schema, subschemaProduct, propSchemaProduct)
        val patternSchemaProduct: P? = if (schema.patternPropertySchemas.isEmpty()) {
            null
        } else schema.patternPropertySchemas
            .map { visitPatternPropertySchema(it.key, it.value) }
            .reduce { a, b -> accumulate(schema, a, b) }
        result = accumulate(schema, result, patternSchemaProduct)
            ?: schema.unevaluatedItemsSchema?.accept(this)?.let { accumulate(schema, result, it) }
            ?: schema.unevaluatedPropertiesSchema?.accept(this)?.let { accumulate(schema, result, it) }
        return result
    }

    open fun visitTrueSchema(schema: TrueSchema): P? = visitChildren(schema)
    open fun visitFalseSchema(schema: FalseSchema): P? = visitChildren(schema)
    open fun visitMinLengthSchema(schema: MinLengthSchema): P? = visitChildren(schema)
    open fun visitMaxLengthSchema(schema: MaxLengthSchema): P? = visitChildren(schema)
    open fun visitAllOfSchema(schema: AllOfSchema): P? = visitChildren(schema)
    open fun visitAnyOfSchema(schema: AnyOfSchema): P? = visitChildren(schema)
    open fun visitOneOfSchema(schema: OneOfSchema): P? = visitChildren(schema)
    open fun visitReferenceSchema(schema: ReferenceSchema): P? = visitChildren(schema)
    open fun visitDynamicRefSchema(schema: DynamicRefSchema): P? = visitChildren(schema)
    open fun visitAdditionalPropertiesSchema(schema: AdditionalPropertiesSchema): P? = visitChildren(schema)
    open fun visitConstSchema(schema: ConstSchema): P? = visitChildren(schema)
    open fun visitEnumSchema(schema: EnumSchema): P? = visitChildren(schema)
    open fun visitTypeSchema(schema: TypeSchema): P? = visitChildren(schema)
    open fun visitMultiTypeSchema(schema: MultiTypeSchema): P? = visitChildren(schema)
    open fun visitPropertySchema(property: String, schema: Schema): P? = visitChildren(schema)
    open fun visitPatternPropertySchema(pattern: Regexp, schema: Schema): P? = visitChildren(schema)
    open fun visitPatternSchema(schema: PatternSchema): P? = visitChildren(schema)
    open fun visitNotSchema(schema: NotSchema): P? = visitChildren(schema)
    open fun visitRequiredSchema(schema: RequiredSchema): P? = visitChildren(schema)
    open fun visitMaximumSchema(schema: MaximumSchema): P? = visitChildren(schema)
    open fun visitMinimumSchema(schema: MinimumSchema): P? = visitChildren(schema)
    open fun visitExclusiveMaximumSchema(schema: ExclusiveMaximumSchema): P? = visitChildren(schema)
    open fun visitExclusiveMinimumSchema(schema: ExclusiveMinimumSchema): P? = visitChildren(schema)
    open fun visitMultipleOfSchema(schema: MultipleOfSchema): P? = visitChildren(schema)
    open fun visitMinItemsSchema(schema: MinItemsSchema): P? = visitChildren(schema)
    open fun visitMaxItemsSchema(schema: MaxItemsSchema): P? = visitChildren(schema)
    open fun visitMinPropertiesSchema(schema: MinPropertiesSchema): P? = visitChildren(schema)
    open fun visitMaxPropertiesSchema(schema: MaxPropertiesSchema): P? = visitChildren(schema)
    open fun visitUniqueItemsSchema(schema: UniqueItemsSchema): P? = visitChildren(schema)
    open fun visitItemsSchema(schema: ItemsSchema): P? = visitChildren(schema)
    open fun visitPrefixItemsSchema(schema: PrefixItemsSchema): P? = visitChildren(schema)
    open fun visitContainsSchema(schema: ContainsSchema): P? = visitChildren(schema)
    open fun visitIfThenElseSchema(schema: IfThenElseSchema): P? = visitChildren(schema)
    open fun visitDependentSchemas(schema: DependentSchemasSchema): P? = visitChildren(schema)
    open fun visitDependentRequiredSchema(schema: DependentRequiredSchema): P? = visitChildren(schema)
    open fun visitUnevaluatedItemsSchema(schema: UnevaluatedItemsSchema): P? = visitChildren(schema)
    open fun visitUnevaluatedPropertiesSchema(schema: UnevaluatedPropertiesSchema): P? = visitChildren(schema)
    open fun visitFormatSchema(schema: FormatSchema): P? = visitChildren(schema)
    open fun visitReadOnlySchema(readOnlySchema: ReadOnlySchema): P? = visitChildren(readOnlySchema)
    open fun visitWriteOnlySchema(writeOnlySchema: WriteOnlySchema): P? = visitChildren(writeOnlySchema)
    open fun visitPropertyNamesSchema(propertyNamesSchema: PropertyNamesSchema): P? = visitChildren(propertyNamesSchema)

    open fun identity(): P? = null
    open fun identity(parent: Schema): P? = identity()
    open fun accumulate(parent: Schema, previous: P?, current: P?): P? = current ?: previous

    open fun visitChildren(parent: Schema): P? {
        var product: P? = identity(parent)
        for (subschema in parent.subschemas()) {
            val current = subschema.accept(this)
            product = accumulate(parent, product, current)
        }
        return product
    }
}

internal class SchemaNotFoundException(expectedKey: String, actualKey: String) :
    RuntimeException("expected key: $expectedKey, but found: $actualKey")

internal class TraversingSchemaVisitor<P>(vararg keys: String) : SchemaVisitor<P>() {

    private val remainingKeys = keys.asList().toMutableList()

    private fun consume(schema: Schema, key: String, cb: () -> P?): P? {
        if (remainingKeys[0] == key) {
            remainingKeys.removeAt(0)
            if (remainingKeys.isEmpty()) {
                return schema as P
            }
            return cb()
        }
        throw SchemaNotFoundException(key, remainingKeys[0])
    }

    override fun visitCompositeSchema(schema: CompositeSchema): P? {
        if (remainingKeys.isEmpty()) {
            return schema as P
        }
        if (remainingKeys[0] == "title") {
            remainingKeys.removeAt(0)
            if (remainingKeys.isEmpty()) {
                return schema.title!!.value as P
            }
            throw SchemaNotFoundException("cannot traverse keys of string 'title'", "")
        } else if (remainingKeys[0] == "properties") {
            remainingKeys.removeAt(0)
            val propName = remainingKeys.removeAt(0)
            return schema.propertySchemas[propName]?.accept(this)
        }
        return super.visitCompositeSchema(schema)
    }

    override fun visitDynamicRefSchema(schema: DynamicRefSchema): P? = consume(schema, "\$dynamicRef") {
        schema.referredSchema?.accept(this)
    }

    override fun visitAdditionalPropertiesSchema(schema: AdditionalPropertiesSchema): P? =
        consume(schema, "additionalProperties") { super.visitAdditionalPropertiesSchema(schema) }

    override fun visitReferenceSchema(schema: ReferenceSchema): P? {
        if (remainingKeys[0] == "\$ref") {
            remainingKeys.removeAt(0)
            if (remainingKeys.isEmpty()) {
                return schema.referredSchema as P
            }
            return schema.referredSchema!!.accept(this)
        }
        throw SchemaNotFoundException("\$ref", remainingKeys[0])
    }
}
