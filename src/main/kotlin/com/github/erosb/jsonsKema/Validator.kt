package com.github.erosb.jsonsKema

import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI

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

enum class FormatValidationPolicy {
    /**
     * Validation against the "format" keyword is always enabled, irrespective of
     * the meta-schema contents.
     */
    ALWAYS,

    /**
     * Validation against the "format" keyword is never done, irrespective of
     * the meta-schema contents.
     */
    NEVER,

    /**
     * Validation against the "format" keyword is enabled only if the meta-schema
     * declares @{code https://json-schema.org/draft/2020-12/vocab/format-assertion}
     * in its @{code $vocabularies} object.
     *
     * Be aware that this is the default behavior, and the draft2020-12 meta-schema
     * does NOT declare the format-assertion vocabulary, hence format validation will be
     * disabled.
     */
    DEPENDS_ON_VOCABULARY
}

enum class ReadWriteContext {
    READ, WRITE, NONE
}

data class ValidatorConfig @JvmOverloads constructor(
    val validateFormat: FormatValidationPolicy = FormatValidationPolicy.DEPENDS_ON_VOCABULARY,
    val readWriteContext: ReadWriteContext = ReadWriteContext.NONE,
    val additionalFormatValidators: Map<String, FormatValidator> = emptyMap()
) {
    companion object {

        class ValidatorConfigBuilder {

            var readWriteContext: ReadWriteContext = ReadWriteContext.NONE
            var validateFormat: FormatValidationPolicy = FormatValidationPolicy.DEPENDS_ON_VOCABULARY
            val additionalFormatValidators: MutableMap<String, FormatValidator> = mutableMapOf()

            fun readWriteContext(readWriteContext: ReadWriteContext): ValidatorConfigBuilder {
                this.readWriteContext = readWriteContext
                return this
            }

            fun validateFormat(validateFormat: FormatValidationPolicy): ValidatorConfigBuilder {
                this.validateFormat = validateFormat;
                return this
            }

            fun additionalFormatValidator(formatName: String, formatValidator: FormatValidator): ValidatorConfigBuilder {
                additionalFormatValidators[formatName] = formatValidator
                return this;
            }

            fun build() = ValidatorConfig(validateFormat, readWriteContext, additionalFormatValidators)
        }

        @JvmStatic
        fun builder(): ValidatorConfigBuilder = ValidatorConfigBuilder()
    }
}

interface Validator {

    companion object {

        @JvmStatic
        fun create(schema: Schema, config: ValidatorConfig): Validator {
            return DefaultValidator(schema, config)
        }

        @JvmStatic
        fun forSchema(schema: Schema): Validator {
            return create(schema, ValidatorConfig(FormatValidationPolicy.DEPENDS_ON_VOCABULARY))
        }
    }

    fun validate(instance: IJsonValue): ValidationFailure?

    fun validate(instanceJson: String, documentSource: URI = DEFAULT_BASE_URI): ValidationFailure? =
        validate(JsonParser(instanceJson, documentSource)())
}

private class MarkableJsonArray<I : IJsonValue>(private val original: IJsonArray<I>) :
    IJsonArray<I> by original {

    val evaluatedIndexes: MutableList<Int> = mutableListOf()

    var allEvaluated: Boolean = false

    override fun markEvaluated(index: Int): IJsonValue {
        evaluatedIndexes.add(index)
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
        return rval.toMap()
    }

    override fun requireArray(): IJsonArray<I> = this

    override fun <P> maybeArray(fn: (IJsonArray<*>) -> P?): P? = fn(this)

    override fun markUnevaluated(idx: Int) {
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

private class DefaultValidator(
    private val rootSchema: Schema,
    private val config: ValidatorConfig
) : Validator, SchemaVisitor<ValidationFailure>() {

    val validateFormat: Boolean = if (config.validateFormat == FormatValidationPolicy.ALWAYS)
        true
    else if (config.validateFormat == FormatValidationPolicy.NEVER)
        false
    else
        when (rootSchema) {
            is CompositeSchema -> rootSchema.vocabulary.isEmpty() || rootSchema.vocabulary.contains(
                "https://json-schema.org/draft/2020-12/vocab/format-assertion"
            )

            else -> false
        }

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
                instance,
                dynamicPath() + Keyword.TYPE
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
                instance,
                dynamicPath() + Keyword.TYPE
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
            MarkableJsonObject(instance as IJsonObj)
        }
    }

    lateinit var instance: IJsonValue

    override fun validate(instance: IJsonValue): ValidationFailure? {
        this.instance = instance
        dynamicPath.rootDocumentSource = rootSchema.location.documentSource
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

    override fun visitConstSchema(schema: ConstSchema): ValidationFailure? = inPathSegment(Keyword.CONST) {
        val isValid = schema.constant == instance
        if (isValid) {
            null
        } else {
            ConstValidationFailure(schema, instance, dynamicPath())
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
                MinLengthValidationFailure(schema, it, dynamicPath() + Keyword.MIN_LENGTH)
            } else {
                null
            }
        }
    }

    override fun visitPropertySchema(property: String, schema: Schema): ValidationFailure? =
        inPathSegment(listOf("properties", property)) {
            if (instance !is IJsonObject<*, *>) {
                return@inPathSegment null
            }
            if (instance.requireObject()[property] === null) {
                return@inPathSegment null
            }
            val propFailure = withOtherInstance(instance.requireObject().get(property)!!) {
                schema.accept(this)
            }
            if (propFailure === null) {
                (instance as IJsonObj).markEvaluated(property)
            }
            return@inPathSegment propFailure
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

    override fun visitPatternSchema(schema: PatternSchema): ValidationFailure? = instance.maybeString { str ->
        schema.pattern.patternMatchingFailure(str.value)?.let {
            PatternValidationFailure(schema, str, dynamicPath() + Keyword.PATTERN)
        }
    }

    override fun visitPropertyNamesSchema(propertyNamesSchema: PropertyNamesSchema): ValidationFailure? = inPathSegment(Keyword.PROPERTY_NAMES) {
        instance.maybeObject { obj ->
            val failures: Map<IJsonString, ValidationFailure> = obj.properties.keys.map {
                it to withOtherInstance(it) {
                    propertyNamesSchema.propertyNamesSchema.accept(this)
                }
            }
                .filter { pair -> pair.second != null }
                .map { it as Pair<IJsonString, ValidationFailure> }
                .toMap()
            return@maybeObject if (failures.isNotEmpty()) {
                PropertyNamesValidationFailure(schema = propertyNamesSchema, instance = obj, causesByProperties = failures, dynamicPath())
            } else {
                null
            }
        }
    }

    private fun <T> withOtherInstance(otherInstance: IJsonValue, cb: () -> T): T {
        val origInstance = instance
        instance = otherInstance
        val rval = cb()
        instance = origInstance
        return rval
    }

    override fun visitAdditionalPropertiesSchema(schema: AdditionalPropertiesSchema): ValidationFailure? = inPathSegment(Keyword.ADDITIONAL_PROPERTIES) {
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
    }

    override fun visitMaxLengthSchema(schema: MaxLengthSchema): ValidationFailure? = inPathSegment(Keyword.MAX_LENGTH) {
        instance.maybeString {
            val length = it.value.codePointCount(0, it.value.length)
            if (length > schema.maxLength) {
                MaxLengthValidationFailure(schema, it, dynamicPath())
            } else {
                null
            }
        }
    }

    override fun visitMinItemsSchema(schema: MinItemsSchema): ValidationFailure? = inPathSegment(Keyword.MIN_ITEMS) {
        instance.maybeArray { array ->
            if (array.length() < schema.minItems.toInt()) {
                MinItemsValidationFailure(schema, array, dynamicPath())
            } else {
                null
            }
        }
    }

    override fun visitMaxItemsSchema(schema: MaxItemsSchema): ValidationFailure? = inPathSegment(Keyword.MAX_ITEMS) {
        instance.maybeArray { array ->
            if (array.length() > schema.maxItems.toInt()) {
                MaxItemsValidationFailure(schema, array, dynamicPath())
            } else {
                null
            }
        }
    }

    override fun visitMinPropertiesSchema(schema: MinPropertiesSchema): ValidationFailure? = instance.maybeObject { obj ->
        if (obj.properties.size < schema.minProperties.toInt()) {
            MinPropertiesValidationFailure(schema, obj, dynamicPath() + Keyword.MIN_PROPERTIES)
        } else {
            null
        }
    }

    override fun visitMaxPropertiesSchema(schema: MaxPropertiesSchema): ValidationFailure? = instance.maybeObject { obj ->
        if (obj.properties.size > schema.maxProperties.toInt()) {
            MaxPropertiesValidationFailure(schema, obj, dynamicPath() + Keyword.MAX_PROPERTIES)
        } else {
            null
        }
    }

    override fun visitNotSchema(schema: NotSchema): ValidationFailure? = if (schema.negatedSchema.accept(this) != null) {
        null
    } else {
        NotValidationFailure(schema, instance, dynamicPath() + Keyword.NOT)
    }

    override fun visitEnumSchema(schema: EnumSchema): ValidationFailure? =
        if (schema.potentialValues.any { it == instance }) {
            null
        } else {
            EnumValidationFailure(schema, instance, dynamicPath() + Keyword.ENUM)
        }

    override fun visitRequiredSchema(schema: RequiredSchema): ValidationFailure? =
        instance.maybeObject {
            val instanceKeys = it.properties.keys.map { it.value }
            val missingProps = schema.requiredProperties.filter { !instanceKeys.contains(it) }
            if (missingProps.isEmpty()) {
                null
            } else {
                RequiredValidationFailure(missingProps, schema, it, dynamicPath() + Keyword.REQUIRED)
            }
        }

    override fun visitMaximumSchema(schema: MaximumSchema): ValidationFailure? = inPathSegment(Keyword.MAXIMUM) {
        instance.maybeNumber {
            if (it.value.toDouble() > schema.maximum.toDouble()) {
                MaximumValidationFailure(schema, it, dynamicPath())
            } else {
                null
            }
        }
    }


    override fun visitMinimumSchema(schema: MinimumSchema): ValidationFailure? = inPathSegment(Keyword.MINIMUM) {
        instance.maybeNumber {
            if (it.value.toDouble() < schema.minimum.toDouble()) {
                MinimumValidationFailure(schema, it, dynamicPath())
            } else {
                null
            }
        }
    }

    override fun visitExclusiveMaximumSchema(schema: ExclusiveMaximumSchema): ValidationFailure? = instance.maybeNumber {
        if (it.value.toDouble() >= schema.maximum.toDouble()) {
            ExclusiveMaximumValidationFailure(schema, it, dynamicPath() + Keyword.EXCLUSIVE_MAXIMUM)
        } else {
            null
        }
    }

    override fun visitExclusiveMinimumSchema(schema: ExclusiveMinimumSchema): ValidationFailure? = instance.maybeNumber {
        if (it.value.toDouble() <= schema.minimum.toDouble()) {
            ExclusiveMinimumValidationFailure(schema, it, dynamicPath() + Keyword.EXCLUSIVE_MINIMUM)
        } else {
            null
        }
    }

    override fun visitMultipleOfSchema(schema: MultipleOfSchema): ValidationFailure? = instance.maybeNumber {
        if (getAsBigDecimal(it.value).remainder(getAsBigDecimal(schema.denominator)).compareTo(BigDecimal.ZERO) == 0) {
            null
        } else {
            MultipleOfValidationFailure(schema, it, dynamicPath() + Keyword.MULTIPLE_OF)
        }
    }

    override fun visitFalseSchema(schema: FalseSchema): ValidationFailure? = inPathSegment(Keyword.FALSE) {
        FalseValidationFailure(schema, instance, dynamicPath())
    }

    override fun visitUniqueItemsSchema(schema: UniqueItemsSchema): ValidationFailure? = inPathSegment(Keyword.UNIQUE_ITEMS) {
        if (schema.unique) {
            instance.maybeArray { array ->
                val occurrences = mutableMapOf<IJsonValue, Int>()
                for ((index, elem) in array.elements.withIndex()) {
                    if (occurrences.containsKey(elem)) {
                        return@maybeArray UniqueItemsValidationFailure(
                            listOf(occurrences[elem]!!, index), schema,
                            array, dynamicPath()
                        )
                    }
                    occurrences[elem] = index
                }
                null
            }
        } else null
    }


    override fun visitItemsSchema(schema: ItemsSchema): ValidationFailure? = inPathSegment(Keyword.ITEMS) {
        instance.maybeArray { array ->
            val failures = mutableMapOf<Int, ValidationFailure>()
            for (index in schema.prefixItemCount until array.length()) {
                withOtherInstance(array[index]) {
                    schema.itemsSchema.accept(this) ?.let { failures[index] = it }
                }
            }
            if (failures.isEmpty()) {
                if (array.length() > schema.prefixItemCount) {
                    array.markAllEvaluated()
                }
                null
            } else {
                ItemsValidationFailure(failures.toMap(), schema, array, dynamicPath())
            }
        }
    }

    override fun visitPrefixItemsSchema(schema: PrefixItemsSchema): ValidationFailure? = inPathSegment(Keyword.PREFIX_ITEMS) {
        instance.maybeArray { array ->
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
                PrefixItemsValidationFailure(failures, schema, array, dynamicPath())
            }
        }
    }

    override fun visitContainsSchema(schema: ContainsSchema): ValidationFailure? = inPathSegment(Keyword.CONTAINS) {
        instance.maybeArray { array ->
            if (array.length() == 0) {
                val minContainsIsZero = schema.minContains == 0
                return@maybeArray if (minContainsIsZero) null else ContainsValidationFailure("no array items are valid against \"contains\" subschema, expected minimum is ${schema.minContains}", schema, array, dynamicPath())
            }
            var successCount = 0
            for (idx in 0 until array.length()) {
                val maybeChildFailure = withOtherInstance(array.markEvaluated(idx)) {
                    schema.containedSchema.accept(this)
                }
                if (maybeChildFailure === null) {
                    ++successCount
                } else {
                    array.markUnevaluated(idx)
                }
            }
            if (schema.maxContains != null && schema.maxContains.toInt() < successCount) {
                return@maybeArray ContainsValidationFailure("$successCount array items are valid against \"contains\" subschema, expected maximum is 1", schema, array, dynamicPath())
            }
            if (successCount < schema.minContains.toInt()) {
                val prefix = if (successCount == 0) "no array items are" else if (successCount == 1) "only 1 array item is" else "only $successCount array items are"
                return@maybeArray ContainsValidationFailure("$prefix valid against \"contains\" subschema, expected minimum is ${schema.minContains.toInt()}", schema, array, dynamicPath())
            }
            return@maybeArray if (schema.maxContains == null && schema.minContains == 1 && successCount == 0) {
                ContainsValidationFailure("expected at least 1 array item to be valid against \"contains\" subschema, found 0", schema, array, dynamicPath())
            } else {
                null
            }
        }
    }

    override fun visitAllOfSchema(schema: AllOfSchema): ValidationFailure? = inPathSegment(Keyword.ALL_OF) {
        val subFailures = collectSubschemaFailures(schema)
        if (subFailures.isNotEmpty()) {
            AllOfValidationFailure(schema = schema, instance = instance, causes = subFailures.toSet(), dynamicPath())
        } else {
            null
        }
    }

    private fun collectSubschemaFailures(schema: Schema) =
        schema.subschemas().mapIndexed { index, subschema ->
            inPathSegment(index.toString()) { subschema.accept(this) }
        }.filterNotNull()

    override fun visitAnyOfSchema(schema: AnyOfSchema): ValidationFailure? = inPathSegment(Keyword.ANY_OF) {
        val subFailures = collectSubschemaFailures(schema)
        if (subFailures.size == schema.subschemas.size) {
            AnyOfValidationFailure(schema = schema, instance = instance, causes = subFailures.toSet(), dynamicPath())
        } else {
            null
        }
    }

    override fun visitOneOfSchema(schema: OneOfSchema): ValidationFailure? = inPathSegment(Keyword.ONE_OF) {
        val subFailures = collectSubschemaFailures(schema)
        if ((schema.subschemas.size - subFailures.size) == 1) {
            null
        } else {
            OneOfValidationFailure(schema = schema, instance = instance, causes = subFailures.toSet(), dynamicPath())
        }
    }

    override fun visitIfThenElseSchema(schema: IfThenElseSchema): ValidationFailure? {
        val ifFailure = inPathSegment(Keyword.IF) { schema.ifSchema.accept(this) }
        return if (ifFailure == null) {
            inPathSegment(Keyword.THEN) {
                schema.thenSchema?.accept(this)
            }
        } else {
            inPathSegment(Keyword.ELSE) {
                schema.elseSchema?.accept(this)
            }
        }
    }

    override fun visitDependentSchemas(schema: DependentSchemasSchema): ValidationFailure? = inPathSegment(Keyword.DEPENDENT_SCHEMAS) {
            instance.maybeObject { obj ->
                val failures: MutableMap<String, ValidationFailure> = mutableMapOf()
                schema.dependentSchemas.forEach { propName, schema ->
                    if (obj[propName] != null) {
                        schema.accept(this)?.let { failures[propName] = it }
                    }
                }
                if (failures.isEmpty()) null else {
                    DependentSchemasValidationFailure(schema, instance, failures, dynamicPath())
                }
            }
        }

    override fun visitDependentRequiredSchema(schema: DependentRequiredSchema): ValidationFailure? = inPathSegment(Keyword.DEPENDENT_REQUIRED) {
        instance.maybeObject { obj ->
            val instanceKeys = obj.properties.keys.map { it.value }
            for (entry in schema.dependentRequired.entries) {
                if (instanceKeys.contains(entry.key)) {
                    val missingKeys = entry.value.filter { !instanceKeys.contains(it) }
                    if (missingKeys.isNotEmpty()) {
                        return@maybeObject DependentRequiredValidationFailure(entry.key, missingKeys.toSet(), schema, obj, dynamicPath())
                    }
                }
            }
            null
        }
    }

    override fun visitUnevaluatedItemsSchema(schema: UnevaluatedItemsSchema): ValidationFailure? = inPathSegment(Keyword.UNEVALUATED_ITEMS) {
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
            if (failures.isNotEmpty()) {
                UnevaluatedItemsValidationFailure(failures, schema, instance, dynamicPath())
            } else null
        } else null
    }

    private val formatValidators: Map<String, FormatValidator> = mapOf(
        "date" to dateFormatValidator,
        "date-time" to dateTimeFormatValidator,
        "time" to timeFormatValidator,
        "duration" to durationFormatValidator,
        "uri" to uriFormatValidator,
        "email" to emailFormatValidator,
        "ipv4" to ipv4FormatValidator,
        "ipv6" to ipv6FormatValidator,
        "uuid" to uuidFormatValidator
    ) + config.additionalFormatValidators

    override fun visitFormatSchema(schema: FormatSchema): ValidationFailure? =
        if (validateFormat) {
            formatValidators[schema.format]
                ?.let { it(instance, schema) }
                ?.also { it.dynamicPath = dynamicPath() + Keyword.FORMAT }
        } else {
            null
        }

    override fun visitUnevaluatedPropertiesSchema(schema: UnevaluatedPropertiesSchema): ValidationFailure? = inPathSegment(Keyword.UNEVALUATED_PROPERTIES) {
        val instance = this.instance
        if (instance is MarkableJsonObject<*, *>) {
            val failures = mutableMapOf<String, ValidationFailure>()
            instance.getUnevaluated().forEach { propName, value ->
                withOtherInstance(value) {
                    schema.unevaluatedPropertiesSchema.accept(this) ?.let { failures[propName] = it }
                }
                instance.markEvaluated(propName)
            }
            if (failures.isNotEmpty()) {
                UnevaluatedPropertiesValidationFailure(failures, schema, instance, dynamicPath())
            } else null
        } else null
    }

    override fun visitReadOnlySchema(readOnlySchema: ReadOnlySchema): ValidationFailure? {
        return if (config.readWriteContext != ReadWriteContext.WRITE)
            null
        else
            ReadOnlyValidationFailure(readOnlySchema, instance, dynamicPath() + Keyword.READ_ONLY)
    }

    override fun visitWriteOnlySchema(writeOnlySchema: WriteOnlySchema): ValidationFailure? {
        return if (config.readWriteContext != ReadWriteContext.READ)
            null
        else
            WriteOnlyValidationFailure(writeOnlySchema, instance, dynamicPath() + Keyword.WRITE_ONLY)
    }

    override fun accumulate(parent: Schema, previous: ValidationFailure?, current: ValidationFailure?): ValidationFailure? {
        if (previous === null) {
            return current
        }
        if (current === null) {
            return previous
        }
        return previous.join(parent, instance, current, dynamicPath())
    }
}
