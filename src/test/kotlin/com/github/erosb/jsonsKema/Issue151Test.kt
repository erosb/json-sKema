package com.github.erosb.jsonsKema

import org.junit.jupiter.api.Test
import java.net.URI

class Issue151Test {

    @Test
    fun test() {
        val permittedEnumValues = mutableListOf<JsonValue>()
        for (enumValue in listOf("a","b")) {
            permittedEnumValues.add(JsonString(enumValue))
        }
        val programmaticSchema = JsonObject(
            properties = mapOf(
                JsonString("enum") to JsonArray(permittedEnumValues)
            )
        )
        val config = SchemaLoaderConfig.createDefaultConfig(mapOf(
            URI("http://example.org") to programmaticSchema.toString()
        ))
    }
}
