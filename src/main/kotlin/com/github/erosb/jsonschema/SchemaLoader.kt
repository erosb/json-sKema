package com.github.erosb.jsonschema

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors.toList

class SchemaLoaderConfig(val schemaClient: SchemaClient)

class SchemaLoadingException(msg: String, cause: Throwable): RuntimeException(msg, cause)

internal fun createDefaultConfig() = SchemaLoaderConfig(
    schemaClient = MemoizingSchemaClient(DefaultSchemaClient())
)

/**
 * http://json-schema.org/draft/2020-12/json-schema-core.html#initial-base
 */
val DEFAULT_BASE_URI: String = "mem://input";

internal data class Anchor(
    var json: IJsonValue? = null,
    var lexicalContextBaseURI: URI? = null,
    var schema: Schema? = null,
    var underLoading: Boolean = false,
    val referenceSchemas: MutableList<ReferenceSchema> = mutableListOf()
) {
    fun createReference(location: SourceLocation, refText: String): ReferenceSchema {
        val rval = ReferenceSchema(schema, refText, location)
        referenceSchemas.add(rval)
        return rval
    }

    fun resolveWith(schema: Schema) {
        referenceSchemas.forEach {
            it.referredSchema = schema
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
    private val anchors: MutableMap<String, Anchor> = mutableMapOf(),
    private val dynamicAnchors: MutableMap<String, IJsonObject<*, *>> = mutableMapOf()  
) {

    fun registerRawSchema(id: String, json: IJsonValue): Anchor {
        val anchor = getAnchorByURI(id)
        if (anchor.json !== null && anchor.json !== json) {
            throw IllegalStateException("raw schema already registered by URI $id")
        }
        anchor.json = json;
        return anchor
    }

    fun nextLoadableAnchor(): Anchor? = anchors.values.find { it.isLoadable() }

    fun nextUnresolvedAnchor(): Anchor? = anchors.values.find { it.json === null }

    fun getAnchorByURI(uri: String): Anchor = anchors.getOrPut(removeEmptyFragment(uri)) { Anchor() }

    fun anchorByURI(ref: String): Anchor? = anchors[removeEmptyFragment(ref)]

    private fun removeEmptyFragment(uri: String): String {
        return if (uri.endsWith("#")) uri.substring(0, uri.length - 1) else uri
    }

    fun registerDynamicAnchor(anchorName: String, json: IJsonObject<*, *>) {
        dynamicAnchors[anchorName] = json
    }

}

class SchemaLoader(
    val schemaJson: IJsonValue,
    val config: SchemaLoaderConfig = createDefaultConfig()
) {

    private constructor(
        schemaJson: IJsonValue,
        config: SchemaLoaderConfig,
        loadingState: LoadingState
    ) : this(schemaJson, config) {
        this.loadingState = loadingState
    }

    private fun <R> withBaseUriAdjustment(json: IJsonValue, runnable: () -> R): R {
        val origBaseUri = loadingState.baseURI;
        adjustBaseURI(json)
        try {
            return runnable()
        } finally {
            loadingState.baseURI = origBaseUri
        }
    }

    private var loadingState: LoadingState = LoadingState(schemaJson)

    operator fun invoke(): Schema = loadRootSchema();

    private fun lookupAnchors(json: IJsonValue, baseURI: URI) {
        when (json) {
            is IJsonObject<*, *> -> {
                withBaseUriAdjustment(json) {
                    when (val id = json["\$id"]) {
                        is IJsonString -> {
                            loadingState.registerRawSchema(loadingState.baseURI.toString(), json);
                        }
                    }
                    when (val anchor = json["\$anchor"]) {
                        is IJsonString -> {
                            val resolvedAnchor = loadingState.baseURI.resolve("#" + anchor.value)
                            loadingState.registerRawSchema(resolvedAnchor.toString(), json)
                        }
                    }
                    json.properties
                        .filter { (key, _) -> key.value != "enum" && key.value != "const" }
                        .forEach { (_, value) -> lookupAnchors(value, baseURI) }
                }
            }
        }
    }

    private fun adjustBaseURI(json: IJsonValue) {
        when (json) {
            is IJsonObject<*, *> -> {
                when (val id = json["\$id"]) {
                    is IJsonString -> {
                        loadingState.baseURI = loadingState.baseURI.resolve(id.value)
                    }
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
        loadingState.registerRawSchema(loadingState.baseURI.toString(), schemaJson)

        do {
            val anchor: Anchor? = loadingState.nextLoadableAnchor()
            if (anchor === null) {
                val unresolved: Anchor? = loadingState.nextUnresolvedAnchor()
                if (unresolved === null) {
                    break
                }
                val pair = resolve(unresolved.referenceSchemas[0])
                unresolved.json = pair.first
                unresolved.lexicalContextBaseURI = pair.second
            } else {
                anchor.underLoading = true;

                val origBaseURI = loadingState.baseURI

                anchor.lexicalContextBaseURI?.let {
                    loadingState.baseURI = it
                }
                val schema = doLoadSchema(anchor.json!!)
                loadingState.baseURI = origBaseURI;

                anchor.resolveWith(schema);
                anchor.underLoading = false;
            }
        } while (true)
        return finalRef.referredSchema!!
    }

    private fun resolve(referenceSchema: ReferenceSchema): Pair<IJsonValue, URI> {
        val ref = referenceSchema.ref
        val uri = parseUri(ref)
        val continingRoot: IJsonValue?
        val byURI = loadingState.anchorByURI(uri.toBeQueried.toString())
        if (byURI !== null && byURI.json !== null) {
            continingRoot = byURI.json!!
        } else {
            val reader = BufferedReader(InputStreamReader(config.schemaClient.get(uri.toBeQueried)))
            val string = reader.readText()
            try {
                continingRoot = JsonParser(string, uri.toBeQueried)()
            } catch (ex: JsonParseException) {
                throw SchemaLoadingException("failed to parse json content returned from ${uri.toBeQueried}", ex)
            }
            loadingState.registerRawSchema(uri.toBeQueried.toString(), continingRoot)

            runWithChangedBaseURI(URI(ref)) {
                adjustBaseURI(continingRoot)
                lookupAnchors(continingRoot, uri.toBeQueried)
            }
        }
        if (uri.fragment.isEmpty() || uri.fragment == "#") {
            return Pair(continingRoot, uri.toBeQueried);
        }
        val byURIWithAnchor = loadingState.anchorByURI(ref)
        if (byURIWithAnchor?.json !== null) {
            return Pair(byURIWithAnchor.json!!, URI(ref))
        }
        return evaluateJsonPointer(continingRoot, uri.fragment)
    }

    private fun evaluateJsonPointer(root: IJsonValue, pointer: String): Pair<IJsonValue, URI> {
        val segments = LinkedList(pointer.split("/"))
        if ("#" != segments.poll()) {
            throw Error("invalid json pointer: $pointer")
        }
        fun unescape(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8)
            .replace("~1", "/")
            .replace("~0", "~")

        fun lookupNext(root: IJsonValue, segments: LinkedList<String>): Pair<IJsonValue, URI> {
            return withBaseUriAdjustment(root) {
                if (segments.isEmpty()) {
                    return@withBaseUriAdjustment Pair(root, loadingState.baseURI)
                }
                val segment = unescape(segments.poll())
                when (root) {
                    is IJsonObject<*, *> -> {
                        val child = root[segment]
                        if (child === null) {
                            throw Error("json pointer evaluation error: could not resolve property $segment in $root")
                        }
                        return@withBaseUriAdjustment lookupNext(child, segments)
                    }
                    else -> {
                        throw Error("json pointer evaluation error: could not resolve property $segment")
                    }
                }
            }
        }

        val idKeywordValue: String? = when (root) {
            is IJsonObject<*, *> -> root["\$id"]?.requireString()?.value
            else -> null
        }
        val baseURIofRoot: String = idKeywordValue
            ?: root.location.documentSource?.toString()
            ?: DEFAULT_BASE_URI

        return runWithChangedBaseURI(URI(baseURIofRoot)) {
            lookupNext(root, segments)
        }
    }

    private fun <T> runWithChangedBaseURI(changedBaseURI: URI, task: () -> T): T {
        val origBaseURI = loadingState.baseURI;
        loadingState.baseURI = changedBaseURI;
        try {
            return task()
        } finally {
            loadingState.baseURI = origBaseURI
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
        adjustBaseURI(schemaJson)
        var propertySchemas: Map<String, Schema> = emptyMap()
        return withBaseUriAdjustment(schemaJson) {
            schemaJson.properties.forEach { (name, value) ->
                var subschema: Schema? = null
                when (name.value) {
                    "minLength" -> subschema = MinLengthSchema(value.requireInt(), name.location)
                    "maxLength" -> subschema = MaxLengthSchema(value.requireInt(), name.location)
                    "allOf" -> subschema = createAllOfSubschema(name.location, value.requireArray())
                    "additionalProperties" -> subschema = AdditionalPropertiesSchema(loadChild(value), name.location)
                    "properties" -> propertySchemas = loadPropertySchemas(value.requireObject())
                    "\$ref" -> subschema = createReferenceSchema(name.location, value.requireString())
//                    "\$dynamicRef" -> subschema = createDynamicRefSchema(name.location, value.requireString())
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
            return@withBaseUriAdjustment CompositeSchema(
                subschemas = subschemas,
                location = schemaJson.location,
//            id = id,
                title = title,
                description = description,
                readOnly = readOnly,
                writeOnly = writeOnly,
                deprecated = deprecated,
                default = default,
                propertySchemas = propertySchemas
            )
        }
    }

    private fun createDynamicRefSchema(location: SourceLocation, dynamicRef: IJsonString): Schema? {
        return DynamicRefSchema(null, dynamicRef.value, location);
    }

    private fun loadPropertySchemas(schemasMap: IJsonObject<*, *>): Map<String, Schema> {
        val rval = mutableMapOf<String, Schema>()
        schemasMap.properties.forEach { (name, value) ->
            rval[name.value] = loadChild(value)
        }
        return rval.toMap()
    }

    private fun createReferenceSchema(location: SourceLocation, ref: IJsonString): ReferenceSchema {
        val s: String = loadingState.baseURI.resolve(ref.value).toString()
        val anchor = loadingState.getAnchorByURI(s)
        return anchor.createReference(location, s)
    }

    private fun loadChild(schemaJson: IJsonValue): Schema {
        return SchemaLoader(schemaJson, config, loadingState).doLoadSchema(schemaJson)
    }

    private fun createAllOfSubschema(location: SourceLocation, subschemas: IJsonArray<*>) = AllOfSchema(
        subschemas.elements.stream()
            .map { loadChild(it) }
            .collect(toList()),
        location
    )

}
