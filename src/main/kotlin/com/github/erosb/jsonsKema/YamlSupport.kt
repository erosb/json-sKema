package com.github.erosb.jsonsKema

import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import java.net.URI

class YamlParseException(override val cause: Throwable): RuntimeException(cause)

internal fun loadFromYaml(node: Node, documentSource: URI) = loadFromYaml(node, JsonPointer(), documentSource)

internal fun loadFromYaml(node: Node, ptr: JsonPointer = JsonPointer(), documentSource: URI = DEFAULT_BASE_URI): JsonValue {
    val location = SourceLocation(
            node.startMark.line + 1,
            node.startMark.column + 1,
            ptr,
            documentSource,
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
            } else if (node.tag == Tag.FLOAT) {
                return JsonNumber(node.value.toDouble(), location)
            }
        }
        is MappingNode -> {
            val props = node.value.map {
                val nextPtr = ptr + (it.keyNode as ScalarNode).value
                val jsonPropName = loadFromYaml(it.keyNode, ptr, documentSource).requireString() as JsonString
                val jsonPropValue = loadFromYaml(it.valueNode, nextPtr, documentSource)
                jsonPropName as IJsonString to jsonPropValue as IJsonValue
            }.toMap()
            return JsonObject(props, location)
        }
        is SequenceNode -> {
            val items = node.value.mapIndexed { index, childNode ->
                val childPtr = ptr + index.toString()
                loadFromYaml(childNode, childPtr, documentSource)
            }
            return JsonArray(items, location)
        }
    }
    TODO("unhandled type ${node.javaClass} / ${node.tag}")
}
