package com.github.erosb.jsonschema

abstract class Schema(open val location: SourceLocation) {
    abstract fun accept(visitor: Visitor);
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

data class CompositeSchema(val subschemas: Set<Schema>, override val location: SourceLocation): Schema(location) {
    override fun accept(visitor: Visitor) = visitor.visitCompositeSchema(this)
}
