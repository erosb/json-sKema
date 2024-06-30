package com.github.erosb.jsonsKema

import java.net.URI

internal fun findFirstCodeOutsidePackage(): SourceLocation {
    val elem = Thread.currentThread().stackTrace.find {
        it.methodName != "getStackTrace"
                && !it.className.startsWith("com.github.erosb.jsonsKema.")
    }
    return elem?.let {
        SourceLocation(
            pointer = JsonPointer(listOf("")),
            documentSource = URI("classpath://" + elem.className),
            lineNumber = elem.lineNumber,
            position = 0
        )
    } ?: UnknownSource
}

class SchemaBuilder private constructor(
    subschemas: List<Schema>,
) {
    companion object {
        private fun type(typeValue: String): TypeSchema =
            TypeSchema(
                JsonString(typeValue, findFirstCodeOutsidePackage()),
                findFirstCodeOutsidePackage(),
            )

        fun typeString(): SchemaBuilder = SchemaBuilder(listOf(type("string")))
    }

    private val subschemas: MutableList<Schema> = subschemas.toMutableList()

    private fun addSubschema(schema: Schema) {
        subschemas.add(schema)
    }

    fun minLength(minLength: Int): SchemaBuilder {
        addSubschema(MinLengthSchema(minLength, findFirstCodeOutsidePackage()))
        return this
    }

    fun maxLength(maxLength: Int): SchemaBuilder {
        addSubschema(MaxLengthSchema(maxLength, findFirstCodeOutsidePackage()))
        return this
    }

    fun build(): Schema =
        CompositeSchema(
            subschemas = subschemas.toSet(),
            location = findFirstCodeOutsidePackage(),
        )
}
