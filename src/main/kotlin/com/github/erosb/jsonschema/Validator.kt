package com.github.erosb.jsonschema

import java.math.BigDecimal
import java.math.BigInteger

internal fun getAsBigDecimal(number: Any): BigDecimal {
    return if (number is BigDecimal) {
        number
    } else if (number is BigInteger) {
        BigDecimal(number)
    } else if (number is Int || number is Long) {
        BigDecimal((number as Number).toLong())
    } else {
        val d = (number as Number).toDouble()
        BigDecimal.valueOf(d)
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
            } else TypeValidationFailure(
                actualType,
                this.schema,
                instance
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
            } else MultiTypeValidationFailure(
                actualType,
                this.schema,
                instance
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
            ConstValidationFailure(schema, instance)
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
                MinLengthValidationFailure(schema, it)
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
        val rval = schema.accept(this)
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
                        schema,
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
                MaxLengthValidationFailure(schema, it)
            } else {
                null
            }
        }
    }

    override fun visitNotSchema(schema: NotSchema): ValidationFailure? = if (schema.negatedSchema.accept(this) != null) {
        null
    } else {
        NotValidationFailure(schema, instance)
    }

    override fun visitRequiredSchema(schema: RequiredSchema): ValidationFailure? =
        instance.maybeObject {
            val instanceKeys = it.properties.keys.map { it.value }
            val missingProps = schema.requiredProperties.filter { !instanceKeys.contains(it) }
            if (missingProps.isEmpty()) {
                null
            } else {
                RequiredValidationFailure(missingProps, schema, it)
            }
        }

    override fun visitMaximumSchema(schema: MaximumSchema): ValidationFailure? = instance.maybeNumber {
        if (it.value.toDouble() > schema.maximum.toDouble()) {
            MaximumValidationFailure(schema, it)
        } else {
            null
        }
    }

    override fun visitMinimumSchema(schema: MinimumSchema): ValidationFailure? = instance.maybeNumber {
        if (it.value.toDouble() < schema.minimum.toDouble()) {
            MinimumValidationFailure(schema, it)
        } else {
            null
        }
    }

    override fun visitMultipleOfSchema(schema: MultipleOfSchema): ValidationFailure? = instance.maybeNumber {
        if (getAsBigDecimal(it.value).remainder(getAsBigDecimal(schema.denominator)).compareTo(BigDecimal.ZERO) == 0) {
            null
        } else {
            MultipleOfValidationFailure(schema, it)
        }
    }

    override fun visitFalseSchema(schema: FalseSchema): ValidationFailure =
        FalseValidationFailure(schema, instance)

    override fun visitUniqueItemsSchema(schema: UniqueItemsSchema): ValidationFailure? = if (schema.unique) {
        instance.maybeArray { array ->
            val occurrences = mutableMapOf<IJsonValue, Int>()
            for ((index, elem) in array.elements.withIndex()) {
                if (occurrences.containsKey(elem)) {
                    return@maybeArray UniqueItemsValidationFailure(listOf(occurrences[elem]!!, index), schema, array)
                }
                occurrences[elem] = index
            }
            null
        }
    } else null

    override fun visitItemsSchema(schema: ItemsSchema): ValidationFailure? = instance.maybeArray { array ->
        val failures = mutableMapOf<Int, ValidationFailure>()
        array.elements.forEachIndexed { index, it ->
            val backup = instance
            instance = it
            try {
                schema.itemsSchema.accept(this) ?. let { failures[index] = it }
            } finally {
                instance = backup
            }
        }
        if (failures.isEmpty()) {
            null
        } else {
            ItemsValidationFailure(failures.toMap(), schema, array)
        }
    }

    override fun visitContainsSchema(schema: ContainsSchema): ValidationFailure? = instance.maybeArray { array ->
        if (array.length() == 0) {
            val minContainsIsZero = schema.minContains == 0
            return@maybeArray if (minContainsIsZero) null else ContainsValidationFailure("no array items are valid against \"contains\" subschema, expected minimum is ${schema.minContains}", schema, array)
        }
        var successCount = 0
        array.elements.forEach {
            val backup = instance
            instance = it
            try {
                val maybeChildFailure = schema.containedSchema.accept(this)
                if (maybeChildFailure === null) {
                    ++successCount
                    if (schema.minContains == 1 && schema.maxContains === null) {
                        return@maybeArray null
                    }
                }
            } finally {
                instance = backup
            }
        }
        if (schema.maxContains != null && schema.maxContains.toInt() < successCount) {
            return@maybeArray ContainsValidationFailure("$successCount array items are valid against \"contains\" subschema, expected maximum is 1", schema, array)
        }
        if (successCount < schema.minContains.toInt()) {
            val prefix = if (successCount == 0) "no array items are" else if (successCount == 1) "only 1 array item is" else "only $successCount array items are"
            return@maybeArray ContainsValidationFailure("$prefix valid against \"contains\" subschema, expected minimum is ${schema.minContains.toInt()}", schema, array)
        }
        return@maybeArray if (schema.maxContains == null && schema.minContains == 1) {
            ContainsValidationFailure("expected at least 1 array item to be valid against \"contains\" subschema, found 0", schema, array)
        } else {
            null
        }
    }

    override fun accumulate(parent: Schema, previous: ValidationFailure?, current: ValidationFailure?): ValidationFailure? {
        if (previous === null) {
            return current
        }
        if (current === null) {
            return previous
        }
        return previous.join(parent, instance, current)
    }
}
