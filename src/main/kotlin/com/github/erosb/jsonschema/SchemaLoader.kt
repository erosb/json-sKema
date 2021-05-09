package com.github.erosb.jsonschema

import java.io.BufferedReader
import java.io.InputStreamReader
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

internal data class Anchor(
    var json: IJsonValue? = null,
    var schema: Schema? = null,
    var underLoading: Boolean = false,
    val referenceSchemas: MutableList<ReferenceSchema> = mutableListOf()
) {
    fun createReference(location: SourceLocation, refText: String): ReferenceSchema {
        val rval = ReferenceSchema(schema, refText, location)
        referenceSchemas.add(rval)
        println("created ref $rval")
        return rval
    }

    fun resolveWith(schema: Schema) {
        referenceSchemas.forEach {
            it.referredSchema = schema
            println("resolved ${it.ref}")
        }
        this.schema = schema
        underLoading = false
    }

    fun isLoaded() = schema !== null

    fun isLoadable() = !isLoaded() && json !== null
}

internal data class LoadingState(
    val documentRoot: IJsonValue,
    var baseURI: URI = URI(DEFAULT_BASE_URI),
    private val anchors: MutableMap<String, Anchor> = mutableMapOf()
) {

    fun registerRawSchema(id: String, json: IJsonValue): Anchor {
        val anchor = anchors.getOrPut(id) { Anchor() }
        if (anchor.json !== null) {
            throw IllegalStateException("raw schema already registered by URI $id")
        }
        anchor.json = json;
        return anchor
    }

    fun nextLoadableAnchor(): Anchor? = anchors.values.find { it.isLoadable() }

    fun nextUnresolvedAnchor(): Anchor? = anchors.values.find { it.json === null }

    fun getAnchorByURI(uri: String): Anchor = anchors.getOrPut(uri) { Anchor() }
    
    fun anchorByURI(ref: String): Anchor? = anchors[ref]

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

    operator fun invoke(): Schema = loadRootSchema();

    private fun lookupAnchors(json: IJsonValue, baseURI: URI) {
        when (json) {
            is IJsonObject<*, *> -> {
                val origBaseUri = loadingState.baseURI;
                adjustBaseURI(json)
                val anchor = json.get("\$anchor");
                if (anchor != null) {
                    val resolvedAnchor = loadingState.baseURI.resolve("#" + anchor.requireString().value)
                    loadingState.registerRawSchema(resolvedAnchor.toString(), json)
                }
                json.properties.forEach { (key, value) ->
                    lookupAnchors(value, baseURI)
                }
                loadingState.baseURI = origBaseUri;
            }
        }
    }

    private fun adjustBaseURI(json: IJsonValue) {
        when (json) {
            is IJsonObject<*, *> -> {
                val id: IJsonString? = json["\$id"]?.requireString()
                id?.let {
                    loadingState.baseURI = loadingState.baseURI.resolve(it.value)
//                    loadingState.registerRawSchema(loadingState.baseURI.toString(), json)
                    println("entering schema with context ${id.value} -> baseURI := ${loadingState.baseURI}")
                }
            }
        }
    }

    private fun loadRootSchema(): Schema {
        adjustBaseURI(schemaJson)
        lookupAnchors(schemaJson, loadingState.baseURI)
        return loadSchema()
    }

    private fun loadSchema(): Schema {
        val finalRef = createReferenceSchema(schemaJson.location, JsonString("#"))
        loadingState.registerRawSchema((loadingState.baseURI?.resolve("#") ?: "#").toString(), schemaJson)

        do {
            val anchor: Anchor? = loadingState.nextLoadableAnchor()
            if (anchor === null) {
                val unresolved: Anchor? = loadingState.nextUnresolvedAnchor()
                if (unresolved === null) {
                    println("breaking")
                    break
                }
                println("itt")
                unresolved.json = resolve(unresolved.referenceSchemas[0])
            } else {
                anchor.underLoading = true;
                val schema = doLoadSchema(anchor.json!!)
                anchor.resolveWith(schema);
                anchor.underLoading = false;
            }
        } while (true)
        return finalRef.referredSchema!!
    }

    private fun resolve(referenceSchema: ReferenceSchema): IJsonValue {
        val ref = referenceSchema.ref
        println(ref)
        val byURI = loadingState.anchorByURI(ref)
        if (byURI !== null && byURI.json !== null) {
            return byURI.json!!
        }
        if (ref.startsWith("#")) {
            TODO("Not yet implemented")       
        } else {
            val uri = parseUri(ref);
            val reader = BufferedReader(InputStreamReader(config.schemaClient.get(uri.toBeQueried)))
            val string = reader.readText()
            val json: IJsonValue = JsonParser(string)()
            return json
//            return lookup(URL(ref), config.schemaClient, loadingState)
        }
    }

    private fun doLoadSchema(schemaJson: IJsonValue): Schema {
        val retval: Schema =
            when (schemaJson) {
                is IJsonBoolean -> if (schemaJson.value)
                    TrueSchema(schemaJson.location)
                else
                    FalseSchema(schemaJson.location)
                is IJsonObject<*, *> -> createCompositeSchema(schemaJson)
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
        val origBaseURI: URI = loadingState.baseURI!!
        adjustBaseURI(schemaJson)
        schemaJson.properties.forEach { (name, value) ->
            var subschema: Schema? = null
            when (name.value) {
                "minLength" -> subschema = MinLengthSchema(value.requireInt(), name.location)
                "maxLength" -> subschema = MaxLengthSchema(value.requireInt(), name.location)
                "allOf" -> subschema = createAllOfSubschema(name.location, value.requireArray())
                "additionalProperties" -> subschema = AdditionalPropertiesSchema(loadChild(value), name.location)
                "\$ref" -> subschema = createReferenceSchema(name.location, value.requireString())
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
//            id = id,
            title = title,
            description = description,
            readOnly = readOnly,
            writeOnly = writeOnly,
            deprecated = deprecated,
            default = default
        )
        loadingState.baseURI = origBaseURI
        return retval
    }

    private fun createReferenceSchema(location: SourceLocation, ref: IJsonString): ReferenceSchema {
        val s: String = (loadingState.baseURI?.resolve(ref.value) ?: ref.value).toString()
        println("create/lookup anchor for ${s}")
        val anchor = loadingState.getAnchorByURI(s)
        return anchor.createReference(location, loadingState.baseURI.resolve(ref.value).toString())
    }

    private fun loadChild(schemaJson: IJsonValue): Schema {
        return SchemaLoader(schemaJson, config, loadingState, documentRoot).doLoadSchema(schemaJson)
    }

    private fun createAllOfSubschema(location: SourceLocation, subschemas: IJsonArray<*>) = AllOfSchema(
        subschemas.elements.stream()
            .map { loadChild(it) }
            .collect(toList()),
        location
    )

}
