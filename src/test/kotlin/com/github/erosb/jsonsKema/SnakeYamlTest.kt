package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import java.io.BufferedReader
import java.io.InputStreamReader

class YamlJsonObject(override val location: SourceLocation) : IJsonObject<IJsonString, IJsonValue> {

    override val properties: Map<IJsonString, IJsonValue> = mapOf()

    override fun markUnevaluated(propName: String) {
    }

    override fun markEvaluated(propName: String) {
    }

}


class SnakeYamlTest {

    @Test
    fun YamlNodeToJsonValue(){
        val n = Yaml().compose(
            BufferedReader(InputStreamReader(this::class.java.getResourceAsStream("/yaml/hello.yaml")))
        )

        dump(n)
    }

    private fun dump(n: Node) {
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
                
            }
            else -> TODO(n::class.toString())
        }
    }
}
