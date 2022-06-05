package com.github.erosb.jsonschema

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class TypeValidationTest {

    companion object {
        @JvmStatic
        fun params(): List<Arguments> {
            return listOf(
                Arguments.of("string", "number", JsonNumber(30.2)),
                Arguments.of("boolean", "number", JsonNumber(30.2)),
                Arguments.of("array", "number", JsonNumber(30.2)),
                Arguments.of("object", "number", JsonNumber(30.2)),
                Arguments.of("null", "number", JsonNumber(30.2)),

                Arguments.of("number", "boolean", JsonBoolean(true)),
                Arguments.of("string", "boolean", JsonBoolean(true)),
                Arguments.of("null", "boolean", JsonBoolean(true)),
                Arguments.of("array", "boolean", JsonBoolean(true)),
                Arguments.of("object", "boolean", JsonBoolean(true)),

                Arguments.of("string", "null", JsonNull()),
                Arguments.of("number", "null", JsonNull()),
                Arguments.of("boolean", "null", JsonNull()),
                Arguments.of("array", "null", JsonNull()),
                Arguments.of("object", "null", JsonNull()),

                Arguments.of("null", "string", JsonString("my-str")),
                Arguments.of("number", "string", JsonString("my-str")),
                Arguments.of("boolean", "string", JsonString("my-str")),
                Arguments.of("array", "string", JsonString("my-str")),
                Arguments.of("object", "string", JsonString("my-str")),

                Arguments.of("null", "object", JsonObject(mapOf())),
                Arguments.of("number", "object", JsonObject(mapOf())),
                Arguments.of("string", "object", JsonObject(mapOf())),
                Arguments.of("boolean", "object", JsonObject(mapOf())),
                Arguments.of("array", "object", JsonObject(mapOf())),

                Arguments.of("null", "array", JsonArray(listOf())),
                Arguments.of("string", "array", JsonArray(listOf())),
                Arguments.of("number", "array", JsonArray(listOf())),
                Arguments.of("boolean", "array", JsonArray(listOf())),
                Arguments.of("object", "array", JsonArray(listOf())),
            )
        }
    }

    @ParameterizedTest(name = "expected type: {0}, actual: {1}")
    @MethodSource("params")
    fun `type test`(typeKeywordValue: String, actualType: String, instance: IJsonValue) {
        val schema = JsonParser(
            """
                { "type": "$typeKeywordValue" }
            """.trimIndent()
        )()
        val actual = Validator.forSchema(SchemaLoader(schema)()).validate(instance)

        Assertions.assertEquals(
            JsonParser(
                """
               {
                    "instanceRef": "#",
                    "schemaRef": "#/type",
                    "message": "expected type: $typeKeywordValue, actual: $actualType",
                    "keyword": "type"
               } 
            """
            )(), actual!!.toJSON()
        )
    }
}
