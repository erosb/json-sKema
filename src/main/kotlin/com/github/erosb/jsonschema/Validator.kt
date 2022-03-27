package com.github.erosb.jsonschema

data class ValidationOutcome(val isSuccess: Boolean)

interface Validator {

    companion object {
        fun forSchema(schema: Schema): Validator {
            return DefaultValidator(schema)
        }
    }

    fun validate(instance: IJsonValue): ValidationOutcome
}


private class DefaultValidator(private val schema: Schema): Validator, Visitor<ValidationOutcome>() {

    lateinit var instance: IJsonValue

    override fun validate(instance: IJsonValue): ValidationOutcome {
        this.instance = instance
        return schema.accept(this) ?: ValidationOutcome(true)
    }

    override fun visitConstSchema(schema: ConstSchema): ValidationOutcome? {
        println("visitConstSchema isSuccess? " + (schema.constant == instance))
        return ValidationOutcome(schema.constant == instance)
    }
}
