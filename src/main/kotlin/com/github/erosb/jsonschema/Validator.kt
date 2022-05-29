package com.github.erosb.jsonschema

open class ValidationFailure(
    val message: String,
    val schema: Schema,
    val instance: IJsonValue,
    val keyword: Keyword? = null,
    open val causes: Set<ValidationFailure> = setOf()
) {
    override fun toString(): String {
        return "Line ${instance.location.lineNumber}, character ${instance.location.position}: ${message}"
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

    internal open fun join(other: ValidationFailure): ValidationFailure {
        return AggregatingValidationFailure(schema, instance, setOf(this, other))
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

    override fun join(other: ValidationFailure): ValidationFailure {
        _causes.add(other)
        return this
    }
}

interface Validator {

    companion object {
        fun forSchema(schema: Schema): Validator {
            return DefaultValidator(schema)
        }
    }

    fun validate(instance: IJsonValue): ValidationFailure?
}


private class DefaultValidator(private val schema: Schema) : Validator, SchemaVisitor<ValidationFailure>() {

    lateinit var instance: IJsonValue

    override fun validate(instance: IJsonValue): ValidationFailure? {
        this.instance = instance
        return schema.accept(this)
    }

    override fun visitConstSchema(schema: ConstSchema): ValidationFailure? {
        val isValid = schema.constant == instance
        if (isValid)
            return ValidationFailure("expected constant value: ${schema.constant}", schema, instance, Keyword.CONST)
        else
            return null
    }

    override fun visitMinLengthSchema(schema: MinLengthSchema): ValidationFailure? {
        return instance.maybeString {
            val length = it.value.codePointCount(0, it.value.length)
            if (length < schema.minLength)
                ValidationFailure(
                    "expected minLength: ${schema.minLength}, actual: ${length}",
                    schema,
                    instance,
                    Keyword.MIN_LENGTH
                )
            else
                null
        }
    }

    override fun visitMaxLengthSchema(schema: MaxLengthSchema): ValidationFailure? {
        return instance.maybeString {
            val length = it.value.codePointCount(0, it.value.length)
            if (length > schema.maxLength)
                ValidationFailure(
                    "expected maxLength: ${schema.maxLength}, actual: ${length}",
                    schema,
                    instance,
                    Keyword.MAX_LENGTH
                )
            else
                null
        }
    }

    override fun visitFalseSchema(schema: FalseSchema): ValidationFailure =
        ValidationFailure("false schema always fails", schema, instance, Keyword.FALSE)

    override fun accumulate(previous: ValidationFailure?, current: ValidationFailure?): ValidationFailure? {
        if (previous === null) {
            return current
        }
        if (current === null) {
            return previous
        }
        return previous.join(current);
    }
}
