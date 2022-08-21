package com.github.erosb.jsonschema

open class ValidationFailure(
    val message: String,
    val schema: Schema,
    val instance: IJsonValue,
    val keyword: Keyword? = null,
    open val causes: Set<ValidationFailure> = setOf()
) {
    override fun toString(): String {
        return "Line ${instance.location.lineNumber}, character ${instance.location.position}: $message"
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

private class DefaultValidator(private val rootSchema: Schema) : Validator, SchemaVisitor<ValidationFailure>() {

    abstract inner class AbstractTypeValidatingVisitor : JsonVisitor<ValidationFailure> {
        override fun visitString(str: IJsonString): ValidationFailure? = checkType("string")
        override fun visitBoolean(bool: IJsonBoolean): ValidationFailure? = checkType("boolean")
        override fun visitNumber(num: IJsonNumber): ValidationFailure? = checkType(findActualNumberType(num))
        override fun visitNull(nil: IJsonNull): ValidationFailure? = checkType("null")
        override fun visitArray(arr: IJsonArray<*>): ValidationFailure? = checkType("array")
        override fun visitObject(obj: IJsonObject<*, *>): ValidationFailure? = checkType("object")

        private fun findActualNumberType(num: IJsonNumber): String {
            val numAsString = num.value.toString()
            val dotIndex = numAsString.indexOf('.')
            fun isZeroFractional(): Boolean = numAsString.substring(dotIndex + 1)
                .chars().allMatch { it == '0'.code }
            return if (dotIndex == -1 || isZeroFractional()) {
                "integer"
            } else {
                "number"
            }
        }

        abstract fun checkType(actualType: String): ValidationFailure?
    }

    inner class TypeValidatingVisitor(private val schema: TypeSchema) : AbstractTypeValidatingVisitor() {

        override fun checkType(actualType: String): ValidationFailure? {
            if (actualType == "integer" && schema.type.value == "number") {
                return null
            }
            return if (schema.type.value == actualType) {
                null
            } else ValidationFailure(
                "expected type: ${schema.type.value}, actual: $actualType",
                this.schema,
                instance,
                Keyword.TYPE
            )
        }
    }

    inner class MultiTypeValidatingVisitor(private val schema: MultiTypeSchema) : AbstractTypeValidatingVisitor() {

        override fun checkType(actualType: String): ValidationFailure? {
            val permittedTypes = schema.types.elements.map { it.requireString().value }
            if (actualType == "integer" && permittedTypes.contains("number")) {
                return null
            }
            return if (permittedTypes.contains(actualType)) {
                null
            } else ValidationFailure(
                "expected type: one of ${permittedTypes.joinToString { ", " }}, actual: $actualType",
                this.schema,
                instance,
                Keyword.TYPE
            )
        }
    }

    lateinit var instance: IJsonValue

    override fun validate(instance: IJsonValue): ValidationFailure? {
        this.instance = instance
        return rootSchema.accept(this)
    }

    override fun visitConstSchema(schema: ConstSchema): ValidationFailure? {
        val isValid = schema.constant == instance
        return if (isValid) {
            null
        } else {
            ValidationFailure("expected constant value: ${schema.constant}", schema, instance, Keyword.CONST)
        }
    }

    override fun visitTypeSchema(schema: TypeSchema): ValidationFailure? {
        return instance.accept(TypeValidatingVisitor(schema))
    }

    override fun visitMultiTypeSchema(schema: MultiTypeSchema): ValidationFailure? {
        return instance.accept(MultiTypeValidatingVisitor(schema))
    }

    override fun visitMinLengthSchema(schema: MinLengthSchema): ValidationFailure? {
        return instance.maybeString {
            val length = it.value.codePointCount(0, it.value.length)
            if (length < schema.minLength) {
                ValidationFailure(
                    "expected minLength: ${schema.minLength}, actual: $length",
                    schema,
                    instance,
                    Keyword.MIN_LENGTH
                )
            } else {
                null
            }
        }
    }

    override fun visitPropertySchema(property: String, schema: Schema): ValidationFailure? {
        if (instance !is IJsonObject<*, *>) {
            return null
        }
        if (instance.requireObject()[property] === null) {
            return null
        }
        val origInstance = instance
        instance = instance.requireObject().get(property)!!
        val rval = super.visitPropertySchema(property, schema)
        instance = origInstance
        return rval
    }

    private fun <T> withOtherInstance(otherInstance: IJsonValue, cb: () -> T): T {
        val origInstance = instance
        instance = otherInstance
        val rval = cb()
        instance = origInstance
        return rval
    }

    override fun visitAdditionalPropertiesSchema(schema: AdditionalPropertiesSchema): ValidationFailure? =
        instance.maybeObject { obj ->
            var endResult: ValidationFailure? = null
            obj.properties
                .forEach { (key, value) ->
                    val keyStr = key.value
                    if (schema.keysInProperties.contains(keyStr)) {
                        return@forEach
                    }
                    endResult = accumulate(
                        endResult,
                        withOtherInstance(value) {
                            super.visitAdditionalPropertiesSchema(schema)
                        }
                    )
                }
            endResult
        }

    override fun visitMaxLengthSchema(schema: MaxLengthSchema): ValidationFailure? {
        return instance.maybeString {
            val length = it.value.codePointCount(0, it.value.length)
            if (length > schema.maxLength) {
                ValidationFailure(
                    "expected maxLength: ${schema.maxLength}, actual: $length",
                    schema,
                    instance,
                    Keyword.MAX_LENGTH
                )
            } else {
                null
            }
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
        return previous.join(current)
    }
}
