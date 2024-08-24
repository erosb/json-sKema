package com.github.erosb.jsonsKema_fluent

import com.github.erosb.jsonsKema.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class SchemaBuilderTest {
    @Test
    fun test() {
        val schema = SchemaBuilder
                .typeString()
                .minLength(2)
                .maxLength(3)
                .build()

        val failure = Validator.forSchema(schema).validate(JsonParser("\"\"")())!!

        assertThat(failure.message).isEqualTo("actual string length 0 is lower than minLength 2")
        assertThat(failure.schema.location.lineNumber).isEqualTo(13)
        assertThat(failure.schema.location.documentSource).isEqualTo(URI("classpath://com.github.erosb.jsonsKema_fluent.SchemaBuilderTest"))
        assertThat(failure.schema.location.pointer).isEqualTo(JsonPointer("minLength"))
        SourceLocation(
            lineNumber = 13,
            position = 0,
            documentSource = URI("classpath://com.github.erosb.jsonsKema_fluent.SchemaBuilderTest"),
            pointer = JsonPointer(listOf("minLength")),
        )
    }

    @Test
    fun `object properties`() {
        val schema = SchemaBuilder
                .typeObject()
                .property(
                    "propA",
                    SchemaBuilder
                        .typeString()
                        .minLength(4),
                ).property("arrayProp", SchemaBuilder.typeArray())
                .build()

        val failure =
            Validator.forSchema(schema).validate(
                JsonParser(
                    """
                    {
                      "propA": "bad"
                    }
                    """.trimIndent(),
                )(),
            )!!

        assertThat(failure.message).isEqualTo("actual string length 3 is lower than minLength 4")
        assertThat(failure.schema.location.lineNumber).isGreaterThan(30)
        assertThat(failure.schema.location.documentSource).isEqualTo(URI("classpath://com.github.erosb.jsonsKema_fluent.SchemaBuilderTest"))
        assertThat(failure.schema.location.pointer).isEqualTo(JsonPointer("properties", "propA", "minLength"))

        val typeFailure =
            Validator.forSchema(schema).validate(
                JsonParser(
                    """
                    {
                        "arrayProp": {}
                    }
                    """.trimIndent(),
                )(),
            )!!

        assertThat(typeFailure.schema.location.pointer).isEqualTo(JsonPointer("properties", "arrayProp", "type"))
    }

    @Test
    fun `regex patterns`() {
        val subject = SchemaBuilder.typeObject()
            .property("propA", SchemaBuilder.typeString()
                .pattern("\\d{2}.*"))
            .property("propB", SchemaBuilder.typeObject()
                .patternProperties(mapOf(
                    "[A-Z]{2}" to SchemaBuilder.typeString()
                ))
                .additionalProperties(SchemaBuilder.falseSchema())
            )
            .build()

        val actual = Validator.forSchema(subject).validate(JsonParser("""
            {
                "propA": "1asd",
                "propB": {
                    "HU": 0,
                    "additional": "bad"
                }
            }
        """.trimIndent())())!!

        actual.causes.find { it.message.contains("instance value did not match pattern \\d{2}.*") }!!
        actual.causes.find { it.schema.location.pointer.toString() == "#/properties/propB" }!!
            .let {
                it.causes.find { it.message == "false schema always fails" } !!
                it.causes.find { it.message == "expected type: string, actual: integer" } !!
            }
    }

    @Test
    fun `array props`() {
        val schema = SchemaBuilder
                .typeArray()
                .minItems(2)
                .maxItems(5)
                .items(SchemaBuilder.typeObject()
                        .property("propA", SchemaBuilder.typeString()),
                ).contains(SchemaBuilder.typeObject()
                        .property(
                            "containedProp", SchemaBuilder.typeArray()
                                .items(SchemaBuilder.typeNumber())
                        ),
                ).minContains(2, SchemaBuilder.typeObject())
                .maxContains(5, SchemaBuilder.typeObject())
                .uniqueItems()
                .build()

        val minItemsLine =
            schema.subschemas()
                .find { it is MinItemsSchema }!!
                .location.lineNumber
        val maxItemsLine =
            schema.subschemas()
                .find { it is MaxItemsSchema }!!
                .location.lineNumber
        assertThat(maxItemsLine).isEqualTo(minItemsLine + 1)

        val itemsSchema = schema.subschemas().find { it is ItemsSchema }!! as ItemsSchema
        assertThat(itemsSchema.location.pointer).isEqualTo(JsonPointer("items"))
        val itemsSchemaLine = itemsSchema.location.lineNumber
        assertThat(itemsSchemaLine).isEqualTo(maxItemsLine + 1)
        assertThat((itemsSchema.itemsSchema as CompositeSchema)
                .propertySchemas["propA"]!!
                .subschemas()
                .find { it is TypeSchema }!!
                .location.pointer
                .toString(),
        ).isEqualTo("#/items/properties/propA/type")

        val containsLine = schema.subschemas()
                .find { it is ContainsSchema }!!
                .location.lineNumber
        assertThat(containsLine).isEqualTo(itemsSchemaLine + 2)
    }

    @Test
    fun moreTypes() {
        val schema = SchemaBuilder.typeObject()
            .property("nullProp", SchemaBuilder.typeNull())
            .property("boolProp", SchemaBuilder.typeBoolean())
            .property("typeInteger", SchemaBuilder.typeInteger())
            .build()

        val expected = CompositeSchema(propertySchemas = mapOf(
            "nullProp" to CompositeSchema(subschemas = setOf(
                TypeSchema(JsonString("null"), UnknownSource)
            )),
            "boolProp" to CompositeSchema(subschemas = setOf(
                TypeSchema(JsonString("boolean"), UnknownSource)
            )),
            "typeInteger" to CompositeSchema(subschemas = setOf(
                TypeSchema(JsonString("integer"), UnknownSource)
            ))
        ), subschemas = setOf(TypeSchema(JsonString("object"), UnknownSource))
        )
        assertBuiltSchema(schema, expected)
    }

    @Test
    fun moreObjectProps() {
        val schema = SchemaBuilder.typeObject()
            .additionalProperties(SchemaBuilder.falseSchema())
            .minProperties(2)
            .maxProperties(3)
            .propertyNames(SchemaBuilder.typeString().minLength(3))
            .required("prop1", "prop2")
            .dependentRequired(mapOf(
                "prop3" to listOf("prop4", "prop5")
            ))
            .readOnly(true)
            .writeOnly(true)
            .build()

        val expected = CompositeSchema(subschemas = setOf(
            TypeSchema(JsonString("object"), UnknownSource),
            AdditionalPropertiesSchema(FalseSchema(UnknownSource), listOf(), listOf(), UnknownSource),
            MinPropertiesSchema(2, UnknownSource),
            MaxPropertiesSchema(3, UnknownSource),
            PropertyNamesSchema(SchemaBuilder.typeString().minLength(3).build(), UnknownSource),
            RequiredSchema(listOf("prop1", "prop2"), UnknownSource),
            DependentRequiredSchema(mapOf(
                "prop3" to listOf("prop4", "prop5")
            ), UnknownSource),
            ReadOnlySchema(UnknownSource),
            WriteOnlySchema(UnknownSource)
        ))

        assertBuiltSchema(schema, expected)
    }

    private fun assertBuiltSchema(actual: Schema, expected: CompositeSchema) {
        assertThat(actual).usingRecursiveComparison()
            .ignoringFieldsOfTypes(SourceLocation::class.java)
            .isEqualTo(expected)
    }

    @Test
    fun unevaluatedProperties() {
        val schema = SchemaBuilder.empty()
            .unevaluatedProperties(SchemaBuilder.falseSchema())
            .build()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            {
                "propA": "1asd",
            }
        """.trimIndent())())!!

        assertThat(actual)
            .hasFieldOrPropertyWithValue("message", "object properties propA failed to validate against \"unevaluatedProperties\" subschema")
            .hasFieldOrPropertyWithValue("keyword", Keyword.UNEVALUATED_PROPERTIES)
            .matches {fail -> fail.schema.location.pointer.toString() == "#/unevaluatedProperties" }
    }

    @Test
    fun unevaluatedItems() {
        val schema = SchemaBuilder.typeArray()
            .unevaluatedItems(SchemaBuilder.falseSchema())
            .build()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            [1]
        """.trimIndent())())!!

        assertThat(actual)
            .hasFieldOrPropertyWithValue("message", "array items 0 failed to validate against \"unevaluatedItems\" subschema")
            .hasFieldOrPropertyWithValue("keyword", Keyword.UNEVALUATED_ITEMS)
            .matches {fail -> fail.schema.location.pointer.toString() == "#/unevaluatedItems" }
    }

    @Test
    fun ifThenElse() {
        val schema = SchemaBuilder.ifSchema(SchemaBuilder.typeString())
            .thenSchema(SchemaBuilder.empty().minLength(3))
            .elseSchema(SchemaBuilder.typeInteger().minimum(100))
            .build()

        val expected = CompositeSchema(subschemas = setOf(
            IfThenElseSchema(
                CompositeSchema(subschemas = setOf(TypeSchema(JsonString("string"), UnknownSource))),
                CompositeSchema(subschemas = setOf(
                    MinLengthSchema(3, UnknownSource)
                )),
                CompositeSchema(subschemas = setOf(
                    TypeSchema(JsonString("integer"), UnknownSource),
                    MinimumSchema(100, UnknownSource)
                )), UnknownSource
                )
        ))

        assertBuiltSchema(schema, expected)
    }

    @Test
    fun onlyIfThen() {
        val schema = SchemaBuilder.ifSchema(SchemaBuilder.typeString())
            .thenSchema(SchemaBuilder.empty().minLength(5))
            .build()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            "xx"
        """.trimIndent())())!!

        assertThat(actual.message).isEqualTo("actual string length 2 is lower than minLength 5")
        assertThat(actual.schema.location.pointer.toString()).isEqualTo("#/then/minLength")
    }

    @Test
    fun allOf() {
        val schema = SchemaBuilder.allOf(listOf(
            SchemaBuilder.typeObject().property("propA", SchemaBuilder.typeString()),
            SchemaBuilder.empty().property("propB", SchemaBuilder.typeInteger())
        )).build()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            {
                "propA": null,
                "propB": "xx"
            }
        """.trimIndent())())!!

        assertThat(actual.message).isEqualTo("2 subschemas out of 2 failed to validate")
        assertThat(actual.keyword).isEqualTo(Keyword.ALL_OF)
    }

    @Test
    fun oneOf() {
        val schema = SchemaBuilder.oneOf(listOf(
            SchemaBuilder.typeObject().property("propA", SchemaBuilder.typeString()),
            SchemaBuilder.empty().property("propB", SchemaBuilder.typeInteger())
        )).build()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            {
                "propA": null,
                "propB": "xx"
            }
        """.trimIndent())())!!

        assertThat(actual.message).isEqualTo("expected 1 subschema to match out of 2, 0 matched")
        assertThat(actual.keyword).isEqualTo(Keyword.ONE_OF)
    }

    @Test
    fun anyOf() {
        val schema = SchemaBuilder.anyOf(listOf(
            SchemaBuilder.typeObject().property("propA", SchemaBuilder.typeString()),
            SchemaBuilder.empty().property("propA", SchemaBuilder.enumSchema(JsonNumber(2), JsonString("2")))
        )).build()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            {
                "propA": null
            }
        """.trimIndent())())!!

        assertThat(actual.message).isEqualTo("no subschema out of 2 matched")
        assertThat(actual.keyword).isEqualTo(Keyword.ANY_OF)
    }

    @Test
    fun not() {
        val schema = SchemaBuilder.not(SchemaBuilder.typeObject()
            .property("propA", SchemaBuilder.typeNumber().not(
                SchemaBuilder.const(JsonNumber(2))
            ))
        ).build()


        val actual = Validator.forSchema(schema).validate("""
            {
                "propA": 3
            }
        """.trimIndent())!!

        assertThat(actual.message).isEqualTo("negated subschema did not fail")
        assertThat(actual.keyword).isEqualTo(Keyword.NOT)
    }

    @Test
    fun numberKeywords() {
        val schema = SchemaBuilder.typeInteger()
                .exclusiveMinimum(10)
                .exclusiveMaximum(8)
                .multipleOf(3)
                .build()

        val actual = Validator.forSchema(schema).validate("8")!!
        assertThat(actual.causes).hasSize(3)
    }

    @Test
    fun formatSchema() {
        val schema = SchemaBuilder.typeString()
                .format("date")
                .build()

        val actual = Validator.forSchema(schema).validate(
                """
                "not a date"
                """.trimIndent(),
            )!!

        assertThat(actual.message).isEqualTo("instance does not match format 'date'")
        assertThat(actual.keyword).isEqualTo(Keyword.FORMAT)
    }

    @Test
    fun dependentSchemas() {
        val schema = SchemaBuilder.typeObject()
            .dependentSchemas(mapOf(
                "propA" to SchemaBuilder.empty().required("propC"),
                "propD" to SchemaBuilder.typeObject()
                    .property("propB", SchemaBuilder.typeInteger())
            ))
            .build()

        val actual = Validator.forSchema(schema).validate("""
            {
                "propA": true,
                "propB": null,
                "propD": false
            }
        """.trimIndent())!!

        assertThat(actual.message).isEqualTo("some dependent subschemas did not match")
        assertThat(actual.keyword).isEqualTo(Keyword.DEPENDENT_SCHEMAS)
        assertThat(actual.causes).hasSize(2)
    }

}
