package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import org.skyscreamer.jsonassert.JSONAssert


class ValidationFailureTest {

    @Test
    fun toStringWithoutCauses() {
        assertThat(maximumFailure().toString()).isEqualTo(
            """
            http://example.com/my-json: Line 70, character 66: 15 is greater than maximum 12
            Keyword: maximum
            Schema pointer: #/properties/numProp/maximum
            Schema location: Line 10, character 5
            Instance pointer: #/numProp
            Instance location: http://example.com/my-json: Line 70, character 66
        """.trimIndent()
        )
    }

    private fun maximumFailure() = MaximumValidationFailure(
        MaximumSchema(12, SourceLocation(10, 5, JsonPointer(listOf("properties", "numProp", "maximum")), URI("test-uri"))),
        JsonNumber(15, SourceLocation(70, 66, JsonPointer(listOf("numProp")), URI("http://example.com/my-json"))),
        UnknownSource + Keyword.MAXIMUM.value
    )

    private fun minimumFailure() = MinimumValidationFailure(
        MinimumSchema(22, SourceLocation(20, 5, JsonPointer(listOf("properties", "numProp", "minimum")), URI("test-uri"))),
        JsonNumber(15, SourceLocation(70, 66, JsonPointer(listOf("numProp")), URI("http://example.com/my-json"))),
        UnknownSource + Keyword.MINIMUM.value
    )

    @Test
    fun toStringWithCauses() {
        val subject = AggregatingValidationFailure(
            CompositeSchema(
                subschemas = setOf(
                    maximumFailure().schema,
                    minimumFailure().schema
                ),
                location = SourceLocation(1, 1, JsonPointer(listOf()), URI("http://example.com/my-json"))
            ),
            instance = JsonNumber(15, SourceLocation(70, 66, JsonPointer(listOf()), URI("http://example.com/my-json"))),
            causes = setOf(maximumFailure(), minimumFailure()),
            dynamicPath = UnknownSource
        )

        assertThat(subject.toString().replace("\t", "    ")).isEqualTo("""
            http://example.com/my-json: Line 70, character 66: multiple validation failures
            Keyword: null
            Schema pointer: #
            Schema location: Line 1, character 1
            Instance pointer: #
            Instance location: http://example.com/my-json: Line 70, character 66
            Causes:
            
                http://example.com/my-json: Line 70, character 66: 15 is greater than maximum 12
                Keyword: maximum
                Schema pointer: #/properties/numProp/maximum
                Schema location: Line 10, character 5
                Instance pointer: #/numProp
                Instance location: http://example.com/my-json: Line 70, character 66

                http://example.com/my-json: Line 70, character 66: 15 is lower than minimum 22
                Keyword: minimum
                Schema pointer: #/properties/numProp/minimum
                Schema location: Line 20, character 5
                Instance pointer: #/numProp
                Instance location: http://example.com/my-json: Line 70, character 66
        """.trimIndent())
    }

    @Test
    fun flatten() {
        val subject = AggregatingValidationFailure(
            CompositeSchema(
                subschemas = setOf(
                    maximumFailure().schema,
                    minimumFailure().schema
                ),
                location = SourceLocation(1, 1, JsonPointer(listOf()), URI("http://example.com/my-json"))
            ),
            instance = JsonNumber(15, SourceLocation(70, 66, JsonPointer(listOf()), URI("http://example.com/my-json"))),
            causes = setOf(maximumFailure(), minimumFailure()),
            dynamicPath = UnknownSource
        )
        val flattened: List<ValidationFailure> = subject.flatten()

        assertThat(flattened).containsExactlyInAnyOrder(
            minimumFailure(), maximumFailure()
        )
    }

    @Test
    fun flattenNoCauses() {
        val subject = minimumFailure()

        val actual = subject.flatten()

        assertThat(actual).containsExactly(subject)
    }

    @Test
    fun flattenRecursive() {
        val falseSubschema = FalseSchema(UnknownSource)
        val falseFailure = FalseValidationFailure(falseSubschema, JsonNull(UnknownSource), UnknownSource + "false")
        val subject = AggregatingValidationFailure(
            CompositeSchema(
                subschemas = setOf(
                    maximumFailure().schema,
                    minimumFailure().schema
                ),
                location = SourceLocation(1, 1, JsonPointer(listOf()), URI("http://example.com/my-json"))
            ),
            instance = JsonNumber(15, SourceLocation(70, 66, JsonPointer(listOf()), URI("http://example.com/my-json"))),
            causes = setOf(maximumFailure(), AggregatingValidationFailure(
                schema = falseSubschema,
                instance = JsonNull(UnknownSource),
                causes = setOf(
                    minimumFailure(), falseFailure
                ),
                dynamicPath = UnknownSource
            )),
            dynamicPath = UnknownSource
        )

        assertThat(subject.flatten()).containsExactlyInAnyOrder(
            minimumFailure(), maximumFailure(), falseFailure
        )
    }

    @Test
    fun issue26Test() {
        val doc = """{
              "customerName": "acme",
              "acquireDate": "2020-12-12"
            }"""
        val jsonValue = JsonParser(doc).parse()
        val userSchema = """{
              "type": "object",
              "properties": {
                "age": {
                  "type": "integer",
                  "minimum": 0
                }
              },
              "additionalProperties": false,
              "required": [
                "age"
              ]
            }
            """

        val schemaJson = JsonParser(userSchema).parse()
        val loadedSchema = SchemaLoader(schemaJson).load()
        val validator = Validator.forSchema(loadedSchema)
        JSONAssert.assertEquals(
            """
                {
                  "instanceRef": "#",
                  "schemaRef": "#/additionalProperties",
                  "message": "multiple validation failures",
                  "dynamicPath": "#/additionalProperties",
                  "causes": [
                    {
                      "instanceRef": "#/customerName",
                      "schemaRef": "#/additionalProperties",
                      "dynamicPath": "#/additionalProperties/false",
                      "message": "false schema always fails",
                      "keyword": "false"
                    },
                    {
                      "instanceRef": "#/acquireDate",
                      "schemaRef": "#/additionalProperties",
                      "dynamicPath": "#/additionalProperties/false",
                      "message": "false schema always fails",
                      "keyword": "false"
                    },
                    {
                      "instanceRef": "#",
                      "schemaRef": "#/required",
                      "dynamicPath": "#/required",
                      "message": "required properties are missing: age",
                      "keyword": "required"
                    }
                  ]
                }
            """.trimIndent(),
            validator.validate(jsonValue)?.toJSON().toString(),
            false
        )
    }
}
