package com.github.erosb.jsonschema

import java.net.URI
import java.util.stream.Collectors.toList

class SchemaLoaderConfig(val schemaClient: SchemaClient)

internal fun createDefaultConfig() = SchemaLoaderConfig(
        schemaClient = DefaultSchemaClient()
)

fun createSchemaLoaderForString(schemaJson: String): SchemaLoader {
    return SchemaLoader(schemaJson = JsonParser(schemaJson)())
}

private data class Reference(
        val refLocation: SourceLocation,
        val ref: String
)

private data class LoadingState(
        val pendingReferences: MutableMap<Reference, ReferenceSchema> = mutableMapOf(),
        val identifiedSchemas: MutableMap<String, Schema> = mutableMapOf(),
        var baseURI: URI? = null
)

class SchemaLoader(
        val schemaJson: IJsonValue,
        val config: SchemaLoaderConfig = createDefaultConfig()
) {

    private constructor(schemaJson: IJsonValue,
                        config: SchemaLoaderConfig,
                        loadingState: LoadingState) : this(schemaJson, config) {
        this.loadingState = loadingState
    }

    private var loadingState: LoadingState = LoadingState()

    operator fun invoke(): Schema {
        val retval = loadSchema()
        loadingState.identifiedSchemas["#"] = retval
        loadingState.pendingReferences.forEach { (ref, refSchema) ->
            refSchema.referredSchema = attemptLookup(ref.ref)
        }
        return retval
    }

    private fun loadSchema(): Schema {
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
        var id: IJsonString? = schemaJson.properties[JsonString("\$id")]?.requireString()
        var origBaseURI: URI? = null
        if (id != null) {
            origBaseURI = loadingState.baseURI
            if (loadingState.baseURI != null) {
                loadingState.baseURI = loadingState.baseURI!!.resolve(id.value);
            } else {
                loadingState.baseURI = URI(id.value);
            }
        }
        schemaJson.properties.forEach { (name, value) ->
            var subschema: Schema? = null
            when (name.value) {
                "minLength" -> subschema = MinLengthSchema(value.requireInt(), name.location)
                "maxLength" -> subschema = MaxLengthSchema(value.requireInt(), name.location)
                "allOf" -> subschema = createAllOfSubschema(name.location, value.requireArray())
                "additionalProperties" -> subschema = AdditionalPropertiesSchema(loadChild(value), name.location)
                "\$ref" -> subschema = createReferenceSchema(name.location, value.requireString())
                "\$id" -> id = value.requireString()
                "title" -> title = value.requireString()
                "description" -> description = value.requireString()
                "readOnly" -> readOnly = value.requireBoolean()
                "writeOnly" -> writeOnly = value.requireBoolean()
                "deprecated" -> deprecated = value.requireBoolean()
                "default" -> default = value
                else -> TODO("unhandled property ${name.value}")
            }
            if (subschema != null) subschemas.add(subschema)
        }
        val retval = CompositeSchema(
                subschemas = subschemas,
                location = schemaJson.location,
                id = id,
                title = title,
                description = description,
                readOnly = readOnly,
                writeOnly = writeOnly,
                deprecated = deprecated,
                default = default
        )
        if (id != null) loadingState.identifiedSchemas[id!!.value] = retval
        loadingState.baseURI = origBaseURI
        return retval
    }

    private fun createReferenceSchema(location: SourceLocation, ref: IJsonString): Schema {
        val referenceSchema = ReferenceSchema(null, location)
        val s: String = (loadingState.baseURI?.resolve(ref.value) ?: ref.value).toString()
        val absoluteRef = if (loadingState.baseURI == null) ref.value else loadingState.baseURI!!.resolve(ref.value).toString()
        loadingState.pendingReferences.put(Reference(location, absoluteRef), referenceSchema)
        return referenceSchema
    }

    private fun attemptLookup(pointer: String): Schema? {
        return loadingState.identifiedSchemas[pointer] ?: loadChild(JsonParser(config.schemaClient.get(URI(pointer)))())
    }

    private fun loadChild(schemaJson: IJsonValue): Schema {
        return SchemaLoader(schemaJson, config, loadingState).loadSchema()
    }

    private fun createAllOfSubschema(location: SourceLocation, subschemas: IJsonArray<*>) = AllOfSchema(
            subschemas.elements.stream()
                    .map { schemaJson -> loadChild(schemaJson) }
                    .collect(toList()),
            location
    )

}
