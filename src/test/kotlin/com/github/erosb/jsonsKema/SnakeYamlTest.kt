package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.parser.ParserException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader


class SnakeYamlTest {

    @Test
    fun readNull() {
        val yaml = Yaml().compose(StringReader("null"))
        val actual = loadFromYaml(yaml)
        assertThat(actual).isEqualTo(JsonNull(
            SourceLocation(1, 1, JsonPointer())
        ))
    }

    @Test
    fun readString() {
        val yaml = Yaml().compose(StringReader("""
            "null"
        """))
        val actual = loadFromYaml(yaml)
        assertThat(actual).isEqualTo(JsonString("null",
            SourceLocation(1, 1, JsonPointer())
        ))
    }

    @Test
    fun readObject() {
        val yaml = Yaml().compose(StringReader("""
            propA: val-a
            propB:  null
        """.trimIndent()))

        val actual = loadFromYaml(yaml)
        assertThat(actual).usingRecursiveComparison().isEqualTo(JsonObject(mapOf(
            JsonString("propA", SourceLocation(1, 1, JsonPointer())) to JsonString("val-a", SourceLocation(1, 8, JsonPointer("propA"))),
            JsonString("propB", SourceLocation(2, 1, JsonPointer())) to JsonNull(SourceLocation(2, 9, JsonPointer("propB")))
        ), SourceLocation(1, 1, JsonPointer())))
    }

    @Test
    fun readSequence() {
        val yaml = Yaml().compose(StringReader("""
            - null
            - "asd"
            - true
        """.trimIndent()))

        val actual = loadFromYaml(yaml)
        assertThat(actual).isEqualTo(JsonArray(listOf(
            JsonNull(),
            JsonString("asd"),
            JsonBoolean(true)
        )))
    }

    @Test
    fun readBooleans() {
        val yaml = Yaml().compose(StringReader("[yes, true, ON, No, false, off]"))
        val actual = loadFromYaml(yaml)
        assertThat(actual).isEqualTo(JsonArray(listOf(
            JsonBoolean(true), JsonBoolean(true), JsonBoolean(true),
            JsonBoolean(false), JsonBoolean(false), JsonBoolean(false)
        )))
    }

    @Test
    fun loadSchemaFromYaml() {
        val schema = SchemaLoader.forURL("classpath://yaml/schema.yml")
    }

    @Test
    fun loadMalformedYamlSchema() {
        assertThatThrownBy { SchemaLoader.forURL("classpath://yaml/malformed.yml") }
            .isInstanceOf(YamlDocumentLoadingException::class.java)
    }

    @Test
    fun loadSchemaFromYamlString() {
        val schema = SchemaLoader("""
            $schema: https://json-schema.org/draft/2020-12/schema
            type: object
            additionalProperties: false
            properties:
              str:
                type: string
              num:
                type: number
                minimum: 0.5
              int:
                type: integer
                maximum: 1
              bool:
                type: boolean
              nullish:
                type: "null"
        """.trimIndent())()
    }
}
