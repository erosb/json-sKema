package com.github.erosb.jsonschema

data class DocumentSource(val filePath: String?) {}

data class SourceLocation(val lineNumber: Int, val position: Int, val documentSource: DocumentSource? = null) {}

open class JsonValue {}

interface LocatedJsonValue {
    val location: SourceLocation
}

data class JsonParseException(override val message: String, val location: SourceLocation): RuntimeException()

open class JsonNull : JsonValue() {}

data class LocatedJsonNull(override val location: SourceLocation): JsonNull(), LocatedJsonValue {}
