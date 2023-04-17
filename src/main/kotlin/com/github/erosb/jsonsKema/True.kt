package com.github.erosb.jsonsKema

data class TrueSchema(override val location: SourceLocation) : Schema(location) {
    override fun <P> accept(visitor: SchemaVisitor<P>) = visitor.visitTrueSchema(this)
}
