package com.github.erosb.jsonschema

interface Visitor {
    fun visitTrueSchema(schema: TrueSchema)
    fun visitFalseSchema(schema: FalseSchema)
    fun visitMinLengthSchema(schema: MinLengthSchema)
    fun visitCompositeSchema(schema: CompositeSchema)
}
