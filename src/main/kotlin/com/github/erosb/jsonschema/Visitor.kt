package com.github.erosb.jsonschema

import java.lang.RuntimeException

abstract class Visitor<P> {

    private val anchors: MutableMap<IJsonString, CompositeSchema> = mutableMapOf()

    internal fun internallyVisitCompositeSchema(schema: CompositeSchema): P? {
        schema.dynamicAnchor ?.let {
            anchors[it] = schema
        }
        var product: P?
        if (schema.dynamicRef != null) {
            println("has dynamicRef ${schema.dynamicRef}")
            val referred = anchors[schema.dynamicRef]
            if (referred === null) {
                TODO("not implemented (no matching dynamicAnchor for dynamicRef ${schema.dynamicRef}")
            }
            val merged = CompositeSchema(
                location = schema.location,
                default = schema.default ?: referred.default,
                subschemas = schema.subschemas ?: referred.subschemas,
                title = schema.title ?: referred.title
            )
            product = visitCompositeSchema(merged)
        } else {
            product = visitCompositeSchema(schema)
        }
        schema.dynamicAnchor ?.let {
            anchors.remove(it)
        }
        return product;
    }
    open fun visitCompositeSchema(schema: CompositeSchema): P? = visitChildren(schema)
    open fun visitTrueSchema(schema: TrueSchema): P? = visitChildren(schema)
    open fun visitFalseSchema(schema: FalseSchema): P? = visitChildren(schema)
    open fun visitMinLengthSchema(schema: MinLengthSchema): P? = visitChildren(schema)
    open fun visitMaxLengthSchema(schema: MaxLengthSchema): P? = visitChildren(schema)
    open fun visitAllOfSchema(schema: AllOfSchema): P? = visitChildren(schema)
    open fun visitReferenceSchema(schema: ReferenceSchema): P? = visitChildren(schema)
    open fun visitDynamicRefSchema(schema: DynamicRefSchema): P? = visitChildren(schema)
    open fun visitAdditionalPropertiesSchema(schema: AdditionalPropertiesSchema): P? = visitChildren(schema)
    open fun identity(): P? = null
    open fun accumulate(previous: P?, current: P?): P? = current ?: previous
    open fun visitChildren(parent: Schema): P? {
        var product: P? = identity()
        for (subschema in parent.subschemas()) {
            val current = subschema.accept(this)
            product = accumulate(product, current);
        }
        return product
    }
}

internal class SchemaNotFoundException(expectedKey: String, actualKey: String) : RuntimeException("expected key: $expectedKey, but found: $actualKey")

internal class TraversingVisitor<P>(vararg keys: String) : Visitor<P>() {

    private val remainingKeys = keys.asList().toMutableList()

    private fun consume(schema: Schema, key: String, cb: () -> P?): P? {
        if (remainingKeys[0] == key) {
            remainingKeys.removeAt(0);
            if (remainingKeys.isEmpty()) {
                return schema as P
            }
            return cb()
        }
        throw SchemaNotFoundException(key, remainingKeys[0]);
    }

    override fun visitCompositeSchema(schema: CompositeSchema): P? {
        if (remainingKeys.isEmpty()) {
            return schema as P
        }
        if (remainingKeys[0] == "title") {
            remainingKeys.removeAt(0)
            if (remainingKeys.isEmpty()) {
                println(schema)
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

    override fun visitAdditionalPropertiesSchema(schema: AdditionalPropertiesSchema): P? = consume(schema, "additionalProperties")
    { super.visitAdditionalPropertiesSchema(schema) }

    override fun visitReferenceSchema(schema: ReferenceSchema): P? {
        if (remainingKeys[0] == "\$ref") {
            remainingKeys.removeAt(0);
            if (remainingKeys.isEmpty()) {
                return schema.referredSchema as P
            }
            return schema.referredSchema!!.accept(this)
        }
        throw SchemaNotFoundException("\$ref", remainingKeys[0]);
    }
}
