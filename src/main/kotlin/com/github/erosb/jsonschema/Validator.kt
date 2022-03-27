package com.github.erosb.jsonschema

data class ValidationOutcome(
    val valid: Boolean,
    val keywordLocation: SourceLocation,
    val instanceLocation: SourceLocation
)

interface Validator {

    companion object {
        fun forSchema(schema: Schema): Validator {
            return DefaultValidator(schema)
        }
    }

    fun validate(instance: IJsonValue): ValidationOutcome
}


private class DefaultValidator(private val schema: Schema) : Validator, Visitor<ValidationOutcome>() {

    lateinit var instance: IJsonValue

    override fun validate(instance: IJsonValue): ValidationOutcome {
        this.instance = instance
        return schema.accept(this) ?: ValidationOutcome(true, schema.location, instance.location)
    }

    override fun visitConstSchema(schema: ConstSchema): ValidationOutcome? {
        return ValidationOutcome(schema.constant == instance, schema.location, instance.location)
    }

    override fun visitMinLengthSchema(schema: MinLengthSchema): ValidationOutcome? {
        if (!(instance is IJsonString)) {
            return null
        }
        val rawString = instance.requireString().value
        val length = rawString.codePointCount(0, rawString.length)
        return ValidationOutcome(length >= schema.minLength, schema.location, instance.location)
    }
}
