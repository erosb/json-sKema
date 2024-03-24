package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class SchemaLoadingFailureTest {

    @Test
    fun `intra-doc failure on object prop`() {
        val subject = SchemaLoader(
            JsonParser(
                """
            {
                "$ref": "#/$defs/missing",
                "$defs": {
                    "Present": false
                }
            }
        """.trimIndent()).parse())

        val expected = RefResolutionException(
            ref = ReferenceSchema(null, "mem://input#/$defs/missing", SourceLocation(2, 5, JsonPointer(listOf("$ref")))),
            missingProperty = "missing",
            resolutionFailureLocation = SourceLocation(3, 14, JsonPointer(listOf("$defs")))
        )

        Assertions.assertThatThrownBy { subject.load() }
            .isEqualTo(expected)
            .hasMessage("$ref resolution failure: could not evaluate pointer \"mem://input#/$defs/missing\", property \"missing\" not found at Line 3, character 14")
    }

    @Test
    fun `not array`() {
        val subject = SchemaLoader(
            JsonParser(
                """
            {
                "type": "object",
                "properties": {
                    "prop": {
                        "$ref": "#/$defs/0/missing"                    
                    }
                },
                "$defs": []
            }
        """.trimIndent()).parse())

        var expected = null;
        Assertions.assertThatThrownBy { subject.load() }
            .isEqualTo(expected)

    }

}
