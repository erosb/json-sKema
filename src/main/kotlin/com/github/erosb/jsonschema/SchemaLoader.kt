package com.github.erosb.jsonschema

import java.net.URI
import java.util.stream.Collectors.toList

class SchemaLoaderConfig(val schemaClient: SchemaClient)

internal fun createDefaultConfig() = SchemaLoaderConfig(
    schemaClient = MemoizingSchemaClient(DefaultSchemaClient())
)

fun createSchemaLoaderForString(schemaJson: String): SchemaLoader {
    return SchemaLoader(schemaJson = JsonParser(schemaJson)())
}

/**
 * http://json-schema.org/draft/2020-12/json-schema-core.html#initial-base
 */
val DEFAULT_BASE_URI: String = "mem://input";

private data class Reference(
    val refLocation: SourceLocation,
    val ref: String
)

private data class Anchor(
    val json: IJsonValue? = null,
    var schema: Schema? = null,
    var underLoading: Boolean = false,
    val referenceSchemas: MutableList<ReferenceSchema> = mutableListOf()
)

private data class LoadingState(
    val documentRoot: IJsonValue,
    val pendingReferences: MutableMap<Reference, ReferenceSchema> = mutableMapOf(),
    val identifiedSchemas: MutableMap<String, Schema> = mutableMapOf(),
    var baseURI: URI? = null,
    private val anchors: MutableMap<String, Anchor> = mutableMapOf()
) {

    fun registerRawSchema(id: String, json: IJsonValue): Anchor {
        anchors[id] = Anchor(json)
        return anchors[id]!!
    }

}

class SchemaLoader(
    val schemaJson: IJsonValue,
    val config: SchemaLoaderConfig = createDefaultConfig(),
    private val documentRoot: IJsonValue = schemaJson
) {

    private constructor(
        schemaJson: IJsonValue,
        config: SchemaLoaderConfig,
        loadingState: LoadingState,
        documentRoot: IJsonValue
    ) : this(schemaJson, config, documentRoot) {
        this.loadingState = loadingState
    }

    private var loadingState: LoadingState = LoadingState(schemaJson)

    private val schemasByURI: MutableMap<String, Anchor> = hashMapOf()

    operator fun invoke(): Schema = loadRootSchema();

    private fun lookupAnchors(json: IJsonValue, baseURI: URI) {
        when(json) {
            is IJsonObject<*,*> -> {
                val anchor = json.get("\$anchor");
                if (anchor != null) {
                    val resolvedAnchor = baseURI.resolve("#" + anchor.requireString().value)
                    schemasByURI[resolvedAnchor.toString()] = Anchor(json)
                }
                json.properties.forEach { (key, value) ->
                    lookupAnchors(value, baseURI)
                }           
            }
        }
    }

    private fun loadRootSchema(): Schema {
        lookupAnchors(schemaJson, if (loadingState.baseURI != null) loadingState.baseURI!! else URI(DEFAULT_BASE_URI))
        return loadSchema()
    }

    private fun loadSchema(): Schema {
        val retval: Schema = doLoadSchema(schemaJson)
        loadingState.identifiedSchemas["#"] = retval
        var lookupSucceeded: Boolean
        do {
            lookupSucceeded = false;
            loadingState.pendingReferences.forEach { (ref, refSchema) ->
                refSchema.referredSchema = attemptLookup(ref.ref)
                if (refSchema.referredSchema != null) {
                    lookupSucceeded = true
                }
            }
            loadingState.pendingReferences.entries.removeIf { e -> e.value.referredSchema != null }
        } while (lookupSucceeded)
        return retval
    }

    private fun doLoadSchema(schemaJson: IJsonValue): Schema {
        val retval: Schema
        when (schemaJson) {
            is IJsonBoolean -> {
                retval = if (schemaJson.value) TrueSchema(schemaJson.location) else FalseSchema(schemaJson.location)
            }
            is IJsonObject<*, *> -> {
                if (loadingState.baseURI == null) {
                    loadingState.baseURI = URI(DEFAULT_BASE_URI)
                }
                val anchor =
                    loadingState.registerRawSchema(loadingState.baseURI.toString() + schemaJson.location.pointer, schemaJson)
                anchor.underLoading = true;
                val compSchema = createCompositeSchema(schemaJson)
                anchor.underLoading = false;
                loadingState.identifiedSchemas[loadingState.baseURI!!.toString()] = compSchema
                retval = compSchema
            }
            else -> TODO()
        }
        return retval
    }

    private fun createCompositeSchema(schemaJson: IJsonObject<*, *>): Schema {
        val subschemas = mutableSetOf<Schema>()
        var title: IJsonString? = null
        var description: IJsonString? = null
        var readOnly: IJsonBoolean? = null
        var writeOnly: IJsonBoolean? = null
        var deprecated: IJsonBoolean? = null
        var default: IJsonValue? = null
        var id: IJsonString? = schemaJson["\$id"]?.requireString()
        val origBaseURI: URI = loadingState.baseURI!!
        if (id != null) {
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
//                else -> TODO("unhandled property ${name.value}")
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
        val s: String = (loadingState.baseURI?.resolve(ref.value) ?: ref.value).toString()
        val referenceSchema = ReferenceSchema(null, location)
        val anchor = schemasByURI.getOrPut(s) { Anchor() }
        anchor.referenceSchemas.add(referenceSchema);
        return referenceSchema
//        val absoluteRef = if (loadingState.baseURI == null) ref.value else loadingState.baseURI!!.resolve(ref.value).toString()
//        loadingState.pendingReferences.put(Reference(location, absoluteRef), referenceSchema)
//        return referenceSchema
    }

    private fun attemptLookup(refValue: String): Schema? {
//        println("attemptLookup $refValue")
        if (loadingState.identifiedSchemas[refValue] != null) {
            return loadingState.identifiedSchemas[refValue]
        }
        val lookupResult = lookupReference(refValue);
        if (lookupResult != null) {
            loadingState.identifiedSchemas[refValue] = lookupResult
        }
        return lookupResult
//        loadingState.identifiedSchemas.computeIfAbsent(refValue, {lookupReference(it)})
//        return loadingState.identifiedSchemas[refValue] ?: lookupReference(refValue)
    }

    private fun lookupReference(refValue: String): Schema? {
        val uri = parseUri(refValue);
        if (uri.toBeQueried == URI("mem://input")) {
            return loadChild(documentRoot)
        }
        val referencedDocument = config.schemaClient.get(uri.toBeQueried)
        return loadChild(JsonParser(referencedDocument)());
    }

    private fun loadChild(schemaJson: IJsonValue): Schema {
        return SchemaLoader(schemaJson, config, loadingState, documentRoot).loadSchema()
    }

    private fun createAllOfSubschema(location: SourceLocation, subschemas: IJsonArray<*>) = AllOfSchema(
        subschemas.elements.stream()
            .map { schemaJson -> loadChild(schemaJson) }
            .collect(toList()),
        location
    )

}
