package com.github.erosb.jsonschema

class SchemaLoaderConfig()

internal fun createDefaultConfig() = SchemaLoaderConfig()

fun createSchemaLoaderForString(schemaJson: String): SchemaLoader {
    return SchemaLoader(schemaJson = JsonParser(schemaJson)())
}

class SchemaLoader(
        val schemaJson: IJsonValue,
        val config: SchemaLoaderConfig = createDefaultConfig()
) {

    operator fun invoke(): Schema {
        if (schemaJson is IJsonBoolean) {
            return if (schemaJson.value) TrueSchema(schemaJson.location) else FalseSchema(schemaJson.location)
        }
        if (schemaJson is IJsonObject<*, *>) {
            return createCompositeSchema(schemaJson)
        }
        TODO()
    }

    private fun createCompositeSchema(schemaJson: IJsonObject<*, *>): Schema {
        val subschemas = mutableSetOf<Schema>()
        schemaJson.properties.forEach { (name, value) ->
            var subschema: Schema
            when (name.value) {
                "minLength" -> subschema = MinLengthSchema(value.requireInt().value.toInt() , name.location)
                else -> TODO()
            }
            subschemas.add(subschema)
        }
        return CompositeSchema(subschemas, schemaJson.location)
    }

}
