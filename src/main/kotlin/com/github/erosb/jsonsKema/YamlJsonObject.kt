package com.github.erosb.jsonsKema

import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag

class YamlJsonObject(
    override val location: SourceLocation,
) : IJsonObject<IJsonString, IJsonValue> {
    override val properties: Map<IJsonString, IJsonValue> = mapOf()

    override fun markUnevaluated(propName: String) {
    }

    override fun markEvaluated(propName: String) {
    }
}



internal fun loadFromYaml(node: Node, ptr: JsonPointer = JsonPointer()): JsonValue {
    val location = SourceLocation(
            node.startMark.line + 1,
            node.startMark.column + 1,
            ptr,
            null,
        )
    when (node) {
        is ScalarNode -> {
            if (node.tag == Tag.NULL) {
                return JsonNull(location)
            } else if (node.tag == Tag.STR) {
                return JsonString(node.value, location)
            } else if (node.tag == Tag.BOOL) {
                val value = node.value.lowercase() in listOf("yes", "y", "on", "true")
                return JsonBoolean(value, location)
            } else if (node.tag == Tag.INT) {
                return JsonNumber(node.value.toInt(), location)
            }
        }
        is MappingNode -> {
            val props = node.value.map {
                val nextPtr = ptr + (it.keyNode as ScalarNode).value
                loadFromYaml(it.keyNode).requireString() as JsonString to loadFromYaml(it.valueNode, nextPtr)
            }.toMap()
            return JsonObject(props, location)
        }
        is SequenceNode -> {
            val items = node.value.mapIndexed { index, childNode ->
                val childPtr = ptr + index.toString()
                loadFromYaml(childNode, childPtr)
            }
            return JsonArray(items, location)
        }
    }
    TODO("unhandled type ${node.javaClass} / ${node.tag}")
}
