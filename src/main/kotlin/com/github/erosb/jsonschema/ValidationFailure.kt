package com.github.erosb.jsonschema

abstract class ValidationFailure(
    val message: String,
    open val schema: Schema,
    open val instance: IJsonValue,
    val keyword: Keyword? = null,
    open val causes: Set<ValidationFailure> = setOf()
) {
    final override fun toString(): String {
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

    internal open fun join(parent: Schema, instance: IJsonValue, other: ValidationFailure): ValidationFailure {
        return AggregatingValidationFailure(parent, instance, setOf(this, other))
    }
}

data class MinimumValidationFailure(
    override val schema: MinimumSchema,
    override val instance: IJsonNumber
) : ValidationFailure("${instance.value} is lower than minimum ${schema.minimum}", schema, instance, Keyword.MINIMUM)

data class MaximumValidationFailure(
    override val schema: MaximumSchema,
    override val instance: IJsonNumber
) : ValidationFailure("${instance.value} is greater than maximum ${schema.maximum}", schema, instance, Keyword.MINIMUM)

data class TypeValidationFailure(
    val actualInstanceType: String,
    override val schema: TypeSchema,
    override val instance: IJsonValue
) : ValidationFailure("expected type: ${schema.type.value}, actual: $actualInstanceType", schema, instance, Keyword.TYPE)

data class MultiTypeValidationFailure(
    val actualInstanceType: String,
    override val schema: MultiTypeSchema,
    override val instance: IJsonValue
) : ValidationFailure(
    "expected type: one of ${schema.types.elements.joinToString { ", " }}, actual: $actualInstanceType",
    schema,
    instance,
    Keyword.TYPE
)

data class FalseValidationFailure(
    override val schema: FalseSchema,
    override val instance: IJsonValue
) : ValidationFailure("false schema always fails", schema, instance, Keyword.FALSE)

data class RequiredValidationFailure(
    val missingProperties: List<String>,
    override val schema: RequiredSchema,
    override val instance: IJsonObj
) : ValidationFailure(
    "required properties are missing: " + missingProperties.joinToString(),
    schema,
    instance,
    Keyword.REQUIRED
)

data class NotValidationFailure(
    override val schema: Schema,
    override val instance: IJsonValue
) : ValidationFailure("negated subschema did not fail", schema, instance)

data class MaxLengthValidationFailure(
    override val schema: MaxLengthSchema,
    override val instance: IJsonString
) : ValidationFailure(
    "actual string length ${instance.value.length} exceeds maxLength ${schema.maxLength}",
    schema,
    instance,
    Keyword.MAX_LENGTH
)

data class MinLengthValidationFailure(
    override val schema: MinLengthSchema,
    override val instance: IJsonString
) : ValidationFailure(
    "actual string length ${instance.value.length} is lower than minLength ${schema.minLength}",
    schema,
    instance,
    Keyword.MIN_LENGTH
)

data class ConstValidationFailure(
    override val schema: ConstSchema,
    override val instance: IJsonValue
) : ValidationFailure(
    "actual instance is not the same as expected constant value",
    schema,
    instance,
    Keyword.CONST
)

data class UniqueItemsValidationFailure(
    val arrayPositions: List<Int>,
    override val schema: UniqueItemsSchema,
    override val instance: IJsonArray<*>
) : ValidationFailure("the same array element occurs at positions " + arrayPositions.joinToString(", "), schema, instance, Keyword.UNIQUE_ITEMS)

data class ItemsValidationFailure(
    val itemFailures: Map<Int, ValidationFailure>,
    override val schema: ItemsSchema,
    override val instance: IJsonArray<*>
) : ValidationFailure(
    "array items ${itemFailures.keys.joinToString(", ")} failed to validate against \"items\" subschema",
    schema,
    instance,
    Keyword.ITEMS
)

data class ContainsValidationFailure(
    override val schema: ContainsSchema,
    override val instance: IJsonArray<*>
) : ValidationFailure(
    "all array item failed to validate against \"contains\" subschema",
    schema,
    instance,
    Keyword.CONTAINS
)

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
        if (parent != schema) {
            TODO("something went wrong")
        }
        if (instance != this.instance) {
            TODO("something went wrong")
        }
        _causes.add(other)
        return this
    }
}
