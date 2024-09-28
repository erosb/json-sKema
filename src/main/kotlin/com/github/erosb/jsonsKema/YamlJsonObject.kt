package com.github.erosb.jsonsKema

import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
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

internal fun loadFromYaml(node: Node): JsonValue {
    val location = SourceLocation(
            node.startMark.line + 1,
            node.startMark.column + 1,
            JsonPointer(),
            null,
        )

    when (node) {
        is ScalarNode -> {
            if (node.tag == Tag.NULL) {
                return JsonNull(
                    location,
                )
            } else if (node.tag == Tag.STR) {
                return JsonString(
                    node.value,
                    SourceLocation(
                        node.startMark.line + 1,
                        node.startMark.column + 1,
                        JsonPointer(),
                        null,
                    ),
                )
            }
        }
        is MappingNode -> {
            val x = node.value.map { loadFromYaml(it.keyNode).requireString() as JsonString to loadFromYaml(it.valueNode) }.toMap()
            return JsonObject(x)
        }

        else -> TODO()
    }
    TODO()
}
