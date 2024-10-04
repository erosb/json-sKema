package com.github.erosb.jsonsKema

import java.net.URI

sealed class SchemaLoadingException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)

data class RefResolutionException(
    val ref: ReferenceSchema,
    val missingProperty: String,
    val resolutionFailureLocation: SourceLocation)
    : SchemaLoadingException(
    "\$ref resolution failure: could not evaluate pointer \"${ref.ref}\", property \"$missingProperty\" not found at ${resolutionFailureLocation.getLocation()}"
)

data class AggregateSchemaLoadingException(val causes: List<SchemaLoadingException>) : SchemaLoadingException("multiple problems found during schema loading") {

    override fun toString(): String {
        return String.format("Multiple errors found during loading the schema:" +
                causes.map { c -> "${c.message}" }.joinToString(
                    prefix = "%n - ",
                    separator = "%n - "
                ))
    }
}

data class JsonTypeMismatchException(
    override val cause: JsonTypingException,
    val expectedType: String = cause.expectedType,
    val actualType: String = cause.actualType,
    val location: SourceLocation = cause.location
) : SchemaLoadingException(cause.message ?: "", cause)

open class SchemaDocumentLoadingException(open val uri: URI, override val cause: Throwable? = null): SchemaLoadingException(cause?.message ?: "", cause)

data class JsonDocumentLoadingException(override val uri: URI, override val cause: Throwable? = null): SchemaDocumentLoadingException(uri, cause)
