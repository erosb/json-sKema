package com.github.erosb.jsonsKema

abstract class ValidationFailure(
    open val message: String,
    open val schema: Schema,
    open val instance: IJsonValue,
    val keyword: Keyword? = null,
    open val causes: Set<ValidationFailure> = setOf()
) {

    private fun appendTo(sb: StringBuilder, linePrefix: String) {
        sb.append("${linePrefix}${instance.location.getLocation()}: $message\n" +
                "${linePrefix}Keyword: ${keyword?.value}\n"  +
                "${linePrefix}Schema pointer: ${schema.location.pointer}\n" +
                "${linePrefix}Schema location: Line ${schema.location.lineNumber}, character ${schema.location.position}\n" +
                "${linePrefix}Instance pointer: ${instance.location.pointer}\n" +
                "${linePrefix}Instance location: ${instance.location.getLocation()}")
        if (causes.isNotEmpty()) {
            sb.append("\nCauses:")
            for (cause in causes) {
                sb.append("\n\n")
                cause.appendTo(sb, linePrefix + "\t")
            }
        }
    }
    final override fun toString(): String {
        val sb = StringBuilder()
        appendTo(sb, "")
        return sb.toString()
    }

    fun toJSON(): JsonObject {
        val instanceRef = JsonString(instance.location.pointer.toString())
        val json = mutableMapOf<JsonString, JsonValue>(
            JsonString("instanceRef") to instanceRef,
            JsonString("schemaRef") to JsonString(schema.location.pointer.toString()),
            JsonString("message") to JsonString(message)
        )
        keyword?.let { json[JsonString("keyword")] = JsonString(it.value) }
        if (causes.isNotEmpty()) {
            json[JsonString("causes")] = JsonArray(causes.map { failure -> failure.toJSON() })
        }
        return JsonObject(
            properties = json.toMap()
        )
    }

    internal open fun join(parent: Schema, instance: IJsonValue, other: ValidationFailure): ValidationFailure {
        return AggregatingValidationFailure(parent, instance, setOf(this, other))
    }
}

internal class AggregatingValidationFailure(
    schema: Schema,
    instance: IJsonValue,
    causes: Set<ValidationFailure>
) : ValidationFailure("multiple validation failures", schema, instance, null, causes) {

    private var _causes = causes.toMutableSet()
    override val causes: Set<ValidationFailure>
        get() {
            return _causes
        }

    override fun join(parent: Schema, instance: IJsonValue, other: ValidationFailure): ValidationFailure {
        if (instance != this.instance) {
            TODO("something went wrong: $instance vs ${this.instance}")
        }
        if (parent != schema) {
            return super.join(parent, instance, other);
        }
        _causes.add(other)
        return this
    }
}
