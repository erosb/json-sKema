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
        var title: IJsonString? = null
        var description: IJsonString? = null
        var readOnly: IJsonBoolean? = null
        var writeOnly: IJsonBoolean? = null
        var deprecated: IJsonBoolean? = null
        var default: IJsonValue? = null
        schemaJson.properties.forEach { (name, value) ->
            var subschema: Schema? = null
            when (name.value) {
                "minLength" -> subschema = MinLengthSchema(value.requireInt(), name.location)
                "maxLength" -> subschema = MaxLengthSchema(value.requireInt(), name.location)
                "title" -> title = value.requireString()
                "description" -> description = value.requireString()
                "readOnly" -> readOnly = value.requireBoolean()
                "writeOnly" -> writeOnly = value.requireBoolean()
                "deprecated" -> deprecated = value.requireBoolean()
                "default" -> default = value
                else -> TODO()
            }
            if (subschema != null) subschemas.add(subschema)
        }
        return CompositeSchema(
                subschemas = subschemas,
                location = schemaJson.location,
                title = title,
                description = description,
                readOnly = readOnly,
                writeOnly = writeOnly,
                deprecated = deprecated,
                default = default
        )
    }

}
