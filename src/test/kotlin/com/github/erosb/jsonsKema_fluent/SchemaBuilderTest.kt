package com.github.erosb.jsonsKema_fluent

import com.github.erosb.jsonsKema.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class SchemaBuilderTest {
    @Test
    fun test() {
        val schema =
            CompositeSchemaBuilder
                .typeString()
                .minLength(2)
                .maxLength(3)
                .build()

        val failure = Validator.forSchema(schema).validate(JsonParser("\"\"")())!!

        assertThat(failure.message).isEqualTo("actual string length 0 is lower than minLength 2")
        assertThat(failure.schema.location.lineNumber).isEqualTo(14)
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
        val schema =
            CompositeSchemaBuilder
                .typeObject()
                .property(
                    "propA",
                    CompositeSchemaBuilder
                        .typeString()
                        .minLength(4),
                ).property("arrayProp", CompositeSchemaBuilder.typeArray())
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
        val subject = CompositeSchemaBuilder.typeObject()
            .property("propA", CompositeSchemaBuilder.typeString()
                .pattern("\\d{2}.*"))
            .property("propB", CompositeSchemaBuilder.typeObject().patternProperties(mapOf(
                "[A-Z]{2}" to CompositeSchemaBuilder.typeString()
            )))
            .build()

        val actual = Validator.forSchema(subject).validate(JsonParser("""
            {
                "propA": "1asd",
                "propB": {
                    "HU": 0
                }
            }
        """.trimIndent())())!!

        println(actual)

        actual.causes.find { it.message.contains("instance value did not match pattern \\d{2}.*") }!!
        actual.causes.find { it.message.contains("expected type: string, actual: integer") }!!
    }

    @Test
    fun `array props`() {
        val schema =
            CompositeSchemaBuilder
                .typeArray()
                .minItems(2)
                .maxItems(5)
                .items(CompositeSchemaBuilder.typeObject()
                        .property("propA", CompositeSchemaBuilder.typeString()),
                ).contains(CompositeSchemaBuilder.typeObject()
                        .property(
                            "containedProp", CompositeSchemaBuilder.typeArray()
                                .items(CompositeSchemaBuilder.typeNumber())
                        ),
                ).minContains(2, CompositeSchemaBuilder.typeObject())
                .maxContains(5, CompositeSchemaBuilder.typeObject())
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
        val schema = CompositeSchemaBuilder.typeObject()
            .property("nullProp", CompositeSchemaBuilder.typeNull())
            .property("boolProp", CompositeSchemaBuilder.typeBoolean())
            .property("typeInteger", CompositeSchemaBuilder.typeInteger())
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
        assertThat(schema).usingRecursiveComparison()
            .ignoringFieldsOfTypes(SourceLocation::class.java)
            .isEqualTo(expected)
    }

    @Test
    fun moreObjectProps() {
        val schema = CompositeSchemaBuilder.typeObject()
            .minProperties(2)
            .maxProperties(3)
            .propertyNames(CompositeSchemaBuilder.typeString().minLength(3))
            .required("prop1", "prop2")
            .dependentRequired(mapOf(
                "prop3" to listOf("prop4", "prop5")
            ))
            .readOnly(true)
            .writeOnly(true)
            .build()

        val expected = CompositeSchema(subschemas = setOf(
            TypeSchema(JsonString("object"), UnknownSource),
            MinPropertiesSchema(2, UnknownSource),
            MaxPropertiesSchema(3, UnknownSource),
            PropertyNamesSchema(CompositeSchemaBuilder.typeString().minLength(3).build(), UnknownSource),
            RequiredSchema(listOf("prop1", "prop2"), UnknownSource),
            DependentRequiredSchema(mapOf(
                "prop3" to listOf("prop4", "prop5")
            ), UnknownSource),
            ReadOnlySchema(UnknownSource),
            WriteOnlySchema(UnknownSource)
        ))

        assertThat(schema).usingRecursiveComparison()
            .ignoringFieldsOfTypes(SourceLocation::class.java)
            .isEqualTo(expected)
    }

    @Test
    fun unevaluated() {
        val schema = CompositeSchemaBuilder.emptySchema()
            .unevaluatedProperties(CompositeSchemaBuilder.falseSchema())
            .build()

        val actual = Validator.forSchema(schema).validate(JsonParser("""
            {
                "propA": "1asd",
            }
        """.trimIndent())())!!

        println(actual)
        assertThat(actual)
            .hasFieldOrPropertyWithValue("message", "object properties propA failed to validate against \"unevaluatedProperties\" subschema")
            .hasFieldOrPropertyWithValue("keyword", Keyword.UNEVALUATED_PROPERTIES)
            .matches {fail -> fail.schema.location.pointer.toString() == "#/unevaluatedProperties" }
    }
}
