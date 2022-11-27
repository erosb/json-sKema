package com.github.erosb.jsonschema

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors.toList

class SchemaLoaderConfig(val schemaClient: SchemaClient)

class SchemaLoadingException(msg: String, cause: Throwable) : RuntimeException(msg, cause)

internal fun createDefaultConfig() = SchemaLoaderConfig(
    schemaClient = MemoizingSchemaClient(DefaultSchemaClient())
)

/**
 * http://json-schema.org/draft/2020-12/json-schema-core.html#initial-base
 */
val DEFAULT_BASE_URI: String = "mem://input"

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
    private val anchors: MutableMap<String, Anchor> = mutableMapOf()
) {

    fun registerRawSchema(id: String, json: IJsonValue): Anchor {
        val anchor = getAnchorByURI(id)
        if (anchor.json !== null && anchor.json !== json) {
            throw IllegalStateException("raw schema already registered by URI $id")
        }
        anchor.json = json
        return anchor
    }

    fun nextLoadableAnchor(): Anchor? = anchors.values.find { it.isLoadable() }

    fun nextUnresolvedAnchor(): Anchor? = anchors.values.find { it.json === null }

    fun getAnchorByURI(uri: String): Anchor = anchors.getOrPut(removeEmptyFragment(uri)) { Anchor() }

    fun anchorByURI(ref: String): Anchor? = anchors[removeEmptyFragment(ref)]

    private fun removeEmptyFragment(uri: String): String {
        return if (uri.endsWith("#")) uri.substring(0, uri.length - 1) else uri
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
        val origBaseUri = loadingState.baseURI
        adjustBaseURI(json)
        try {
            return runnable()
        } finally {
            loadingState.baseURI = origBaseUri
        }
        val k: Keyword? = null
    }

    private var loadingState: LoadingState = LoadingState(schemaJson)

    operator fun invoke(): Schema = loadRootSchema()

    private fun lookupAnchors(json: IJsonValue, baseURI: URI) {
        if (shouldProceedWithAnchorLookup(json)) return
        when (json) {
            is IJsonObject<*, *> -> {
                withBaseUriAdjustment(json) {
                    when (val id = json[Keyword.ID.value]) {
                        is IJsonString -> {
                            loadingState.registerRawSchema(loadingState.baseURI.toString(), json)
                        }
                    }
                    when (val anchor = json[Keyword.ANCHOR.value]) {
                        is IJsonString -> {
                            val resolvedAnchor = loadingState.baseURI.resolve("#" + anchor.value)
                            loadingState.registerRawSchema(resolvedAnchor.toString(), json)
                        }
                    }
//                    when (val anchor = json[Keyword.DYNAMIC_ANCHOR.value]) {
//                        is IJsonString -> {
//                            val resolvedAnchor = loadingState.baseURI.resolve("#" + anchor.value)
//                            loadingState.registerRawSchema(resolvedAnchor.toString(), json)
//                        }
//                    }
                    json.properties
                        .filter { (key, _) ->
                            key.value != Keyword.ENUM.value && key.value != Keyword.CONST.value
                            //        && Keyword.values().any { it.value == key.value }
                        }
                        .forEach { (_, value) -> lookupAnchors(value, baseURI) }
                }
            }
        }
    }

    /**
     * lookupAnchors() should not proceed with the recursion into values of unknown keywords
     */
    private fun shouldProceedWithAnchorLookup(json: IJsonValue): Boolean {
        val locationSegments = json.location.pointer.segments
        if (locationSegments.isNotEmpty()) {
            val lastSegment = locationSegments.last()
            if (!Keyword.values().any { lastSegment == it.value }) { // last segment is unknown keyword
                if (locationSegments.size == 1) {
                    return true
                }
                val beforeLastSegment = locationSegments[locationSegments.size - 2]
                val beforeLastKeyword = Keyword.values().find { it.value == beforeLastSegment }
                if (beforeLastKeyword == null || !beforeLastKeyword.hasMapLikeSemantics) {
                    return true
                }
            }
        }
        return false
    }

    private fun adjustBaseURI(json: IJsonValue) {
        when (json) {
            is IJsonObject<*, *> -> {
                when (val id = json[Keyword.ID.value]) {
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
                anchor.underLoading = true

                val origBaseURI = loadingState.baseURI

                anchor.lexicalContextBaseURI?.let {
                    loadingState.baseURI = it
                }
                val schema = doLoadSchema(anchor.json!!)
                loadingState.baseURI = origBaseURI

                anchor.resolveWith(schema)
                anchor.underLoading = false
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
            return Pair(continingRoot, uri.toBeQueried)
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
                    is IJsonArray<*> -> {
                        val child = root[Integer.parseInt(segment)]
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
            is IJsonObject<*, *> -> root[Keyword.ID.value]?.requireString()?.value
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
        val origBaseURI = loadingState.baseURI
        loadingState.baseURI = changedBaseURI
        try {
            return task()
        } finally {
            loadingState.baseURI = origBaseURI
        }
    }

    private fun doLoadSchema(schemaJson: IJsonValue): Schema {
        val retval: Schema =
            when (schemaJson) {
                is IJsonBoolean -> if (schemaJson.value) {
                    TrueSchema(schemaJson.location)
                } else {
                    FalseSchema(schemaJson.location)
                }
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
        var dynamicRef: URI? = null
        var dynamicAnchor: URI? = null
        adjustBaseURI(schemaJson)
        var propertySchemas: Map<String, Schema> = emptyMap()
        return withBaseUriAdjustment(schemaJson) {
            schemaJson.properties.forEach { (name, value) ->
                var subschema: Schema? = null
                when (name.value) {
                    Keyword.MIN_LENGTH.value -> subschema = MinLengthSchema(value.requireInt(), name.location)
                    Keyword.MAX_LENGTH.value -> subschema = MaxLengthSchema(value.requireInt(), name.location)
                    Keyword.ALL_OF.value -> subschema = createAllOfSubschema(name.location, value.requireArray())
                    Keyword.ANY_OF.value -> subschema = createAnyOfSubschema(name.location, value.requireArray())
                    Keyword.ONE_OF.value -> subschema = OneOfSchema(arrayToSubschemaList(value.requireArray()), name.location)
                    Keyword.ADDITIONAL_PROPERTIES.value -> subschema = buildAdditionalPropertiesSchema(schemaJson, value, name)
                    Keyword.PROPERTIES.value -> propertySchemas = loadPropertySchemas(value.requireObject())
                    Keyword.REF.value -> subschema = createReferenceSchema(name.location, value.requireString())
                    Keyword.DYNAMIC_REF.value -> dynamicRef = loadingState.baseURI.resolve(value.requireString().value)
                    Keyword.DYNAMIC_ANCHOR.value ->
                        dynamicAnchor =
                            loadingState.baseURI.resolve("#" + value.requireString().value)
                    Keyword.TITLE.value -> title = value.requireString()
                    Keyword.DESCRIPTION.value -> description = value.requireString()
                    Keyword.READ_ONLY.value -> readOnly = value.requireBoolean()
                    Keyword.WRITE_ONLY.value -> writeOnly = value.requireBoolean()
                    Keyword.DEPRECATED.value -> deprecated = value.requireBoolean()
                    Keyword.DEFAULT.value -> default = value
                    Keyword.CONST.value -> subschema = ConstSchema(value, name.location)
                    Keyword.TYPE.value -> {
                        subschema = value.maybeString { TypeSchema(it, name.location) }
                            ?: value.maybeArray { MultiTypeSchema(it, name.location) }
                    }
                    Keyword.NOT.value -> subschema = NotSchema(loadChild(value), name.location)
                    Keyword.REQUIRED.value -> subschema = RequiredSchema(
                        value.requireArray().elements.map { it.requireString().value },
                        name.location
                    )
                    Keyword.MAXIMUM.value -> subschema = MaximumSchema(value.requireNumber().value, name.location)
                    Keyword.MINIMUM.value -> subschema = MinimumSchema(value.requireNumber().value, name.location)
                    Keyword.EXCLUSIVE_MAXIMUM.value -> subschema = ExclusiveMaximumSchema(value.requireNumber().value, name.location)
                    Keyword.EXCLUSIVE_MINIMUM.value -> subschema = ExclusiveMinimumSchema(value.requireNumber().value, name.location)
                    Keyword.MULTIPLE_OF.value -> subschema = MultipleOfSchema(value.requireNumber().value, name.location)
                    Keyword.UNIQUE_ITEMS.value -> subschema = UniqueItemsSchema(value.requireBoolean().value, name.location)
                    Keyword.ITEMS.value -> subschema = ItemsSchema(loadChild(value), name.location)
                    Keyword.CONTAINS.value -> subschema = buildContainsSchema(schemaJson, value, name.location)
                    Keyword.IF.value -> subschema = buildIfThenElseSchema(schemaJson, name.location)
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
                propertySchemas = propertySchemas,
                dynamicRef = dynamicRef?.toString(),
                dynamicAnchor = dynamicAnchor?.toString()
            )
        }
    }

    private fun buildIfThenElseSchema(schemaJson: IJsonObj, location: SourceLocation): Schema {
        val ifSchema = loadChild(schemaJson[Keyword.IF.value]!!)
        val thenSchema = schemaJson[Keyword.THEN.value]?.let { loadChild(it) }
        val elseSchema = schemaJson[Keyword.ELSE.value]?.let { loadChild(it) }
        return IfThenElseSchema(ifSchema, thenSchema, elseSchema, location)
    }

    private fun buildAdditionalPropertiesSchema(
        containingObject: IJsonObject<*, *>,
        value: IJsonValue,
        name: IJsonString
    ): AdditionalPropertiesSchema {
        val keysInProperties = containingObject["properties"]?.requireObject()
            ?.properties?.keys?.map { it.value } ?: listOf()
        return AdditionalPropertiesSchema(loadChild(value), keysInProperties, name.location)
    }

    private fun buildContainsSchema(
        containingObject: IJsonObject<*, *>,
        value: IJsonValue,
        location: SourceLocation
    ): ContainsSchema {
        val minContains = containingObject[Keyword.MIN_CONTAINS.value]?.maybeNumber { it.value } ?: 1
        val maxContains = containingObject[Keyword.MAX_CONTAINS.value]?.maybeNumber { it.value }
        return ContainsSchema(loadChild(value), minContains, maxContains, location)
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
        arrayToSubschemaList(subschemas),
        location
    )
    private fun createAnyOfSubschema(location: SourceLocation, subschemas: IJsonArray<*>) = AnyOfSchema(
        arrayToSubschemaList(subschemas),
        location
    )

    private fun arrayToSubschemaList(subschemas: IJsonArray<*>): List<Schema> =
        subschemas.elements.stream()
            .map { loadChild(it) }
            .collect(toList())
}
