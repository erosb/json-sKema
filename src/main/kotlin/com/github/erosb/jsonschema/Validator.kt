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

private class MarkableJsonArray<I : IJsonValue>(private val original: IJsonArray<I>) :
    IJsonArray<I> by original {

    val evaluatedIndexes: MutableList<Int> = mutableListOf()

    var allEvaluated: Boolean = false

    override fun markEvaluated(index: Int): IJsonValue {
        evaluatedIndexes.add(index)
        println("${System.identityHashCode(this)} marks index $index as evaluated => " + super.get(index))
        return super.get(index)
    }

    override fun markAllEvaluated() {
        this.allEvaluated = true
    }

    fun unevaluatedItems(): Map<Int, IJsonValue> {
        if (allEvaluated) {
            return emptyMap()
        }
        val rval: MutableMap<Int, IJsonValue> = mutableMapOf()
        for (idx in 0 until original.length()) {
            if (!evaluatedIndexes.contains(idx)) {
                rval.put(idx, original[idx])
            }
        }
        println("returning ${rval.size} / ${original.length()} unevaluated items")
        return rval.toMap()
    }

    override fun requireArray(): IJsonArray<I> = this

    override fun <P> maybeArray(fn: (IJsonArray<*>) -> P?): P? = fn(this)

    override fun markUnevaluated(idx: Int) {
        println("mark unevaluated $idx")
        evaluatedIndexes.remove(idx)
    }
}

private class MarkableJsonObject<P : IJsonString, V : IJsonValue>(
    private val original: IJsonObject<P, V>
) : IJsonObject<P, V> by original {

    val evaluatedProperties: MutableList<String> = mutableListOf()

    override fun markEvaluated(propName: String) {
        evaluatedProperties.add(propName)
    }

    override fun markUnevaluated(propName: String) {
        evaluatedProperties.remove(propName)
    }

    fun getUnevaluated(): Map<String, IJsonValue> = original.properties
        .mapKeys { it.key.value }
        .filter { it.key !in evaluatedProperties }
        .toMap()

    override fun requireObject(): IJsonObject<P, V> = this
    override fun <P> maybeObject(fn: (IJsonObject<*, *>) -> P?): P? = fn(this)
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

    private fun markableArray(original: IJsonArray<IJsonValue>): MarkableJsonArray<*> {
        return if (original is MarkableJsonArray<*>) {
            original
        } else {
            MarkableJsonArray(instance as IJsonArray<IJsonValue>)
        }
    }

    private fun markableObject(original: IJsonObj): MarkableJsonObject<*, *> {
        return if (original is MarkableJsonObject<*, *>) {
            original
        } else {
            MarkableJsonObject(instance as IJsonObject<IJsonString, IJsonValue>)
        }
    }

    public lateinit var instance: IJsonValue

    override fun validate(instance: IJsonValue): ValidationFailure? {
        this.instance = instance
        return rootSchema.accept(this)
    }

    override fun visitCompositeSchema(schema: CompositeSchema): ValidationFailure? {
        if (instance is IJsonArray<*> && schema.unevaluatedItemsSchema != null) {
            return withOtherInstance(markableArray(instance as IJsonArray<IJsonValue>)) {
                return@withOtherInstance super.visitCompositeSchema(schema)
            }
        } else if (schema.unevaluatedPropertiesSchema != null && instance is IJsonObj) {
            return withOtherInstance(markableObject(instance as IJsonObj)) {
                return@withOtherInstance super.visitCompositeSchema(schema)
            }
        } else {
            return super.visitCompositeSchema(schema)
        }
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
        val propFailure = withOtherInstance(instance.requireObject().get(property)!!) {
            schema.accept(this)
        }
        if (propFailure === null) {
            (instance as IJsonObj).markEvaluated(property)
        }
        return propFailure
    }

    override fun visitPatternPropertySchema(pattern: Regexp, schema: Schema): ValidationFailure? = instance.maybeObject { obj ->
        val failures = obj.properties
            .filter { pattern.patternMatchingFailure(it.key.value) === null }
            .map {
                val failure = withOtherInstance(it.value) {
                    schema.accept(this)
                }
                if (failure === null) {
                    obj.markEvaluated(it.key.value)
                }
                return@map failure
            }
        failures.firstOrNull()
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
                    if (schema.patternPropertyKeys.any { it.patternMatchingFailure(keyStr) == null }) {
                        return@forEach
                    }
                    val failure = withOtherInstance(value) {
                        super.visitAdditionalPropertiesSchema(schema)
                    }
                    if (failure === null) {
                        obj.markEvaluated(keyStr)
                    }
                    endResult = accumulate(
                        schema,
                        endResult,
                        failure
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

    override fun visitMinItemsSchema(schema: MinItemsSchema): ValidationFailure? = instance.maybeArray { array ->
        if (array.length() < schema.minItems.toInt()) {
            MinItemsValidationFailure(schema, array)
        } else {
            null
        }
    }

    override fun visitMaxItemsSchema(schema: MaxItemsSchema): ValidationFailure? = instance.maybeArray { array ->
        if (array.length() > schema.maxItems.toInt()) {
            MaxItemsValidationFailure(schema, array)
        } else {
            null
        }
    }

    override fun visitMinPropertiesSchema(schema: MinPropertiesSchema): ValidationFailure? = instance.maybeObject { obj ->
        if (obj.properties.size < schema.minProperties.toInt()) {
            MinPropertiesValidationFailure(schema, obj)
        } else {
            null
        }
    }

    override fun visitMaxPropertiesSchema(schema: MaxPropertiesSchema): ValidationFailure? = instance.maybeObject { obj ->
        if (obj.properties.size > schema.maxProperties.toInt()) {
            MaxPropertiesValidationFailure(schema, obj)
        } else {
            null
        }
    }

    override fun visitNotSchema(schema: NotSchema): ValidationFailure? = if (schema.negatedSchema.accept(this) != null) {
        null
    } else {
        NotValidationFailure(schema, instance)
    }

    override fun visitEnumSchema(schema: EnumSchema): ValidationFailure? =
        if (schema.potentialValues.any { it == instance }) {
            null
        } else {
            EnumValidationFailure(schema, instance)
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

    override fun visitExclusiveMaximumSchema(schema: ExclusiveMaximumSchema): ValidationFailure? = instance.maybeNumber {
        if (it.value.toDouble() >= schema.maximum.toDouble()) {
            ExclusiveMaximumValidationFailure(schema, it)
        } else {
            null
        }
    }

    override fun visitExclusiveMinimumSchema(schema: ExclusiveMinimumSchema): ValidationFailure? = instance.maybeNumber {
        if (it.value.toDouble() <= schema.minimum.toDouble()) {
            ExclusiveMinimumValidationFailure(schema, it)
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
        println("itten ${array.javaClass}")
        val failures = mutableMapOf<Int, ValidationFailure>()
        for (index in schema.prefixItemCount until array.length()) {
            withOtherInstance(array[index]) {
                val failure = schema.itemsSchema.accept(this)
                println("$index => $failure")
                if (failure === null) {
//                    array.markAllEvaluated()
                } else {
                    failures[index] = failure
//                    array.markUnevaluated(index)
                }
            }
        }
        if (failures.isEmpty()) {
            if (array.length() > schema.prefixItemCount) {
                array.markAllEvaluated()
            }
            null
        } else {
            ItemsValidationFailure(failures.toMap(), schema, array)
        }
    }

    override fun visitPrefixItemsSchema(schema: PrefixItemsSchema): ValidationFailure? = instance.maybeArray { array ->
        val failures = mutableMapOf<Int, ValidationFailure>()
        for (index in 0 until Math.min(array.length(), schema.prefixSchemas.size)) {
            val subschema = schema.prefixSchemas[index]
            withOtherInstance(array.markEvaluated(index)) {
                val failure = subschema.accept(this)
                if (failure != null) {
                    failures[index] = failure
                    array.markUnevaluated(index)
                }
            }
        }
        if (failures.isEmpty()) {
            null
        } else {
            PrefixItemsValidationFailure(failures, schema, array)
        }
    }

    override fun visitContainsSchema(schema: ContainsSchema): ValidationFailure? = instance.maybeArray { array ->
        if (array.length() == 0) {
            val minContainsIsZero = schema.minContains == 0
            return@maybeArray if (minContainsIsZero) null else ContainsValidationFailure("no array items are valid against \"contains\" subschema, expected minimum is ${schema.minContains}", schema, array)
        }
        var successCount = 0
        for (idx in 0 until array.length()) {
            println("contains accesses array[$idx]")
            val maybeChildFailure = withOtherInstance(array.markEvaluated(idx)) {
                schema.containedSchema.accept(this)
            }
            if (maybeChildFailure === null) {
                ++successCount
            } else {
                array.markUnevaluated(idx)
            }
        }
        println("successCount = $successCount")
        if (schema.maxContains != null && schema.maxContains.toInt() < successCount) {
            return@maybeArray ContainsValidationFailure("$successCount array items are valid against \"contains\" subschema, expected maximum is 1", schema, array)
        }
        if (successCount < schema.minContains.toInt()) {
            val prefix = if (successCount == 0) "no array items are" else if (successCount == 1) "only 1 array item is" else "only $successCount array items are"
            return@maybeArray ContainsValidationFailure("$prefix valid against \"contains\" subschema, expected minimum is ${schema.minContains.toInt()}", schema, array)
        }
        return@maybeArray if (schema.maxContains == null && schema.minContains == 1 && successCount == 0) {
            ContainsValidationFailure("expected at least 1 array item to be valid against \"contains\" subschema, found 0", schema, array)
        } else {
            println("\"contains\" success $successCount")
            null
        }
    }

    override fun visitAllOfSchema(schema: AllOfSchema): ValidationFailure? {
        val subFailures = schema.subschemas.map { subschema -> subschema.accept(this) }.filterNotNull()
        return if (subFailures.isNotEmpty()) {
            AllOfValidationFailure(schema = schema, instance = instance, causes = subFailures.toSet())
        } else {
            null
        }
    }

    override fun visitAnyOfSchema(schema: AnyOfSchema): ValidationFailure? {
        val subFailures = schema.subschemas.map { subschema -> subschema.accept(this) }.filterNotNull()
        return if (subFailures.size == schema.subschemas.size) {
            AnyOfValidationFailure(schema = schema, instance = instance, causes = subFailures.toSet())
        } else {
            null
        }
    }

    override fun visitOneOfSchema(schema: OneOfSchema): ValidationFailure? {
        val subFailures = schema.subschemas.map { subschema -> subschema.accept(this) }.filterNotNull()
        return if ((schema.subschemas.size - subFailures.size) == 1) {
            null
        } else {
            OneOfValidationFailure(schema = schema, instance = instance, causes = subFailures.toSet())
        }
    }

    override fun visitIfThenElseSchema(schema: IfThenElseSchema): ValidationFailure? {
        val ifFailure = schema.ifSchema.accept(this)
        println("ifFailure = $ifFailure")
        return if (ifFailure == null) {
            schema.thenSchema?.accept(this)
        } else {
            println("else? ")
            schema.elseSchema?.accept(this)
        }
    }

    override fun visitDependentSchemas(schema: DependentSchemasSchema): ValidationFailure? = instance.maybeObject { obj ->
        val failures: MutableMap<String, ValidationFailure> = mutableMapOf()
        schema.dependentSchemas.forEach { propName, schema ->
            if (obj[propName] != null) {
                schema.accept(this)?.let { failures[propName] = it }
            }
        }
        if (failures.isEmpty()) null else {
            DependentSchemasValidationFailure(schema, instance, failures)
        }
    }

    override fun visitDependentRequiredSchema(schema: DependentRequiredSchema): ValidationFailure? = instance.maybeObject { obj ->
        val instanceKeys = obj.properties.keys.map { it.value }
        for (entry in schema.dependentRequired.entries) {
            if (instanceKeys.contains(entry.key)) {
                val missingKeys = entry.value.filter { !instanceKeys.contains(it) }
                if (missingKeys.isNotEmpty()) {
                    return@maybeObject DependentRequiredValidationFailure(entry.key, missingKeys.toSet(), schema, obj)
                }
            }
        }
        null
    }

    override fun visitUnevaluatedItemsSchema(schema: UnevaluatedItemsSchema): ValidationFailure? {
        val instance = this.instance
        if (instance is MarkableJsonArray<*>) {
            val failures = mutableMapOf<Int, ValidationFailure>()
            instance.unevaluatedItems().forEach { (index, item) ->
                withOtherInstance(item) {
                    schema.unevaluatedItemsSchema.accept(this)?.let { current ->
                        failures.put(index, current)
                    }
                }
                instance.markEvaluated(index)
            }
            println("uneval failures: $failures")
            return if (failures.isNotEmpty()) {
                UnevaluatedItemsValidationFailure(failures, schema, instance)
            } else null
        } else {
            return null
        }
    }

    override fun visitUnevaluatedPropertiesSchema(schema: UnevaluatedPropertiesSchema): ValidationFailure? {
        val instance = this.instance
        if (instance is MarkableJsonObject<*, *>) {
            val failures = mutableMapOf<String, ValidationFailure>()
            instance.getUnevaluated().forEach { propName, value ->
                withOtherInstance(value) {
                    schema.unevaluatedPropertiesSchema.accept(this) ?.let { failures[propName] = it }
                }
                instance.markEvaluated(propName)
            }
            return if (failures.isNotEmpty()) {
                UnevaluatedPropertiesValidationFailure(failures, schema, instance)
            } else null
        }
        return null
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
