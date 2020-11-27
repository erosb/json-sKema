package com.github.erosb.jsonschema

abstract class Schema(open val location: SourceLocation) {
    abstract fun accept(visitor: Visitor);
}

data class CompositeSchema(
        val subschemas: Set<Schema>,
        override val location: SourceLocation,
        val id: IJsonString? = null,
        val title: IJsonString? = null,
        val description: IJsonString? = null,
        val deprecated: IJsonBoolean? = null,
        val readOnly: IJsonBoolean? = null,
        val writeOnly: IJsonBoolean? = null,
        val default: IJsonValue? = null): Schema(location) {
    override fun accept(visitor: Visitor) = visitor.visitCompositeSchema(this)
}

data class AllOfSchema(
        val subschemas: List<Schema>,
        override val location: SourceLocation
): Schema(location) {
    override fun accept(visitor: Visitor) = visitor.visitAllOfSchema(this)
}

data class ReferenceSchema(var referredSchema: Schema?, override val location: SourceLocation): Schema(location) {
    override fun accept(visitor: Visitor) = visitor.visitReferenceSchema(this)
}

data class TrueSchema(override val location: SourceLocation): Schema(location) {
    override fun accept(visitor: Visitor) = visitor.visitTrueSchema(this)
}

data class FalseSchema(override val location: SourceLocation): Schema(location) {
    override fun accept(visitor: Visitor) = visitor.visitFalseSchema(this)
}

data class MinLengthSchema(val minLength: Int, override val location: SourceLocation): Schema(location){
    override fun accept(visitor: Visitor) = visitor.visitMinLengthSchema(this)
}

data class MaxLengthSchema(val maxLength: Int, override val location: SourceLocation): Schema(location) {
    override fun accept(visitor: Visitor) = visitor.visitMaxLengthSchema(this)
}
