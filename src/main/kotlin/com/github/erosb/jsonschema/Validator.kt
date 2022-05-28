package com.github.erosb.jsonschema

data class ValidationFailure(
    val message: String,
    val schema: Schema,
    val instance: IJsonValue,
    val keyword: Keyword? = null,
    val causes: Set<ValidationFailure> = setOf()
    ) {
    override fun toString(): String {
        return "Line ${instance.location.lineNumber}, character ${instance.location.position}: ${message}"
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


private class DefaultValidator(private val schema: Schema) : Validator, Visitor<ValidationFailure>() {

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

    override fun accumulate(previous: ValidationFailure?, current: ValidationFailure?): ValidationFailure? {
        if (previous === null) {
            return current
        }
        if (current === null) {
            return previous
        }
        return ValidationFailure("multiple validation failures", schema, instance, null, setOf(current, previous))
    }
}
