package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

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
        MaximumSchema(12, SourceLocation(10, 5, JsonPointer(listOf("properties", "numProp", "maximum")))),
        JsonNumber(15, SourceLocation(70, 66, JsonPointer(listOf("numProp")), URI("http://example.com/my-json")))
    )

    private fun minimumFailure() = MinimumValidationFailure(
        MinimumSchema(22, SourceLocation(20, 5, JsonPointer(listOf("properties", "numProp", "minimum")))),
        JsonNumber(15, SourceLocation(70, 66, JsonPointer(listOf("numProp")), URI("http://example.com/my-json")))
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
            causes = setOf(maximumFailure(), minimumFailure())
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
}
