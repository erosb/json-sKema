package com.github.erosb.jsonsKema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
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
    fun YamlNodeToJsonValue(){
        val n = Yaml().compose(
            BufferedReader(InputStreamReader(this::class.java.getResourceAsStream("/yaml/hello.yaml")))
        )

        dump(n)
    }



    private fun dump(n: Node) {
        println("n.type = " +n.javaClass.simpleName + " at " + n.tag)
        when (n) {
            is MappingNode -> {
                n.value.forEach { tuple ->
                    val keyNode = tuple.keyNode as ScalarNode
                    println("${keyNode.value} at ${keyNode.startMark.line} , ${keyNode.startMark.column}")
                    dump(tuple.valueNode)
                }
            }
            is ScalarNode -> {
                println("scalar: ${n.value} at ${n.startMark.line} , ${n.startMark.column}")
            }
            is SequenceNode -> {
                n.value.forEach {
                    dump(it)
                }
            }
            else -> TODO(n::class.toString())
        }
    }
}
