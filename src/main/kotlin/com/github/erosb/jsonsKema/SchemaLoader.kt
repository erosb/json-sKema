package com.github.erosb.jsonsKema

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors.toList

data class SchemaLoaderConfig @JvmOverloads constructor(
    val schemaClient: SchemaClient,
    val initialBaseURI: URI = DEFAULT_BASE_URI,
    val additionalMappings: Map<URI, String> = mapOf()
) {
    companion object {
        @JvmStatic
        fun createDefaultConfig(additionalMappings: Map<URI, String> = mapOf()) = SchemaLoaderConfig(
            schemaClient = SchemaClient.createDefaultInstance(additionalMappings),
            additionalMappings = additionalMappings
        )
    }
}

internal fun createDefaultConfig(additionalMappings: Map<URI, String> = mapOf()) = SchemaLoaderConfig.createDefaultConfig(additionalMappings)

/**
 * http://json-schema.org/draft/2020-12/json-schema-core.html#initial-base
 */
val DEFAULT_BASE_URI = URI("mem://input")

internal data class Knot(
        var json: IJsonValue? = null,
        var lexicalContextBaseURI: URI? = null,
        var schema: Schema? = null,
        var underLoading: Boolean = false,
        val referenceSchemas: MutableList<ReferenceSchema> = mutableListOf(),
        val dynamic: Boolean = false
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
    var vocabulary: List<String>,
    private val anchors: MutableMap<String, Knot> = mutableMapOf(),
    var baseURI: URI
) {

    fun registerRawSchemaByAnchor(id: String, json: IJsonValue): Knot {
        val anchor = getAnchorByURI(id)
        if (anchor.json !== null && anchor.json !== json) {
            throw IllegalStateException("raw schema already registered by URI $id")
        }
        anchor.json = json
        return anchor
    }

    fun nextLoadableAnchor(): Knot? = anchors.values.find { it.isLoadable() }

    fun nextUnresolvedAnchor(): Knot? = anchors.values.find { it.json === null }

    fun getAnchorByURI(uri: String): Knot {
        return anchors.getOrPut(normalizeUri(uri)) { Knot(
            lexicalContextBaseURI = URI(uri)
        ) }
    }

    fun anchorByURI(ref: String): Knot? = anchors[normalizeUri(ref)]

    private fun normalizeUri(uri: String): String {
        val effectiveUri: String
        if (uri.startsWith("file:/") && !uri.startsWith("file:///")) {
            effectiveUri = "file:///" + uri.substring("file:/".length)
        } else {
            effectiveUri = uri
        }
        return if (effectiveUri.endsWith("#")) effectiveUri.substring(0, effectiveUri.length - 1) else effectiveUri
    }

    fun registerRawSchemaByDynAnchor(dynAnchor: String, json: IJsonObject<*, *>) {
        anchors.getOrPut(normalizeUri(dynAnchor)) {Knot(dynamic = true)}.json = json
    }

    fun markUnreachable(unresolved: Knot) {
        anchors.entries.find { entry -> entry.value === unresolved }
            ?. key
            ?. let { anchors.remove(it) }

    }
}

internal data class LoadingContext(
    val containingObject: IJsonObj,
    val keywordValue: IJsonValue,
    val location: SourceLocation,
    val subschemaLoader: (IJsonValue) -> Schema,
    val regexpFactory: RegexpFactory
) {
    fun loadSubschema() = subschemaLoader(keywordValue)
}

internal typealias KeywordLoader = (context: LoadingContext) -> Schema?

class SchemaLoader(
        val schemaJson: IJsonValue,
        val config: SchemaLoaderConfig = createDefaultConfig()
) {

    companion object {

        @JvmStatic
        fun forURL(url: String): SchemaLoader {

            val schemaJson = createDefaultConfig().schemaClient.getParsed(URI(url))
            return SchemaLoader(
                schemaJson = schemaJson,
                config = createDefaultConfig().copy(
                    initialBaseURI = URI(url)
                )
            )
        }

    }

    constructor(schemaJson: IJsonValue) : this(schemaJson, createDefaultConfig())

    @JvmOverloads
    constructor(schemaJson: String, documentSource: URI = DEFAULT_BASE_URI) : this(
        JsonValue.parse(schemaJson, documentSource),
        createDefaultConfig()
    )

    private val regexpFactory: RegexpFactory = JavaUtilRegexpFactory()

    private val keywordLoaders: Map<String, KeywordLoader> = mapOf(
            Keyword.ADDITIONAL_PROPERTIES.value to additionalPropertiesLoader,
            Keyword.TYPE.value to typeLoader,
            Keyword.REQUIRED.value to requiredLoader,
            Keyword.NOT.value to notSchemaLoader,
            Keyword.MIN_LENGTH.value to minLengthLoader,
            Keyword.MAX_LENGTH.value to maxLengthLoader,
            Keyword.MIN_ITEMS.value to minItemsLoader,
            Keyword.MAX_ITEMS.value to maxItemsLoader,
            Keyword.MIN_PROPERTIES.value to minPropertiesLoader,
            Keyword.MAX_PROPERTIES.value to maxPropertiesLoader,
            Keyword.MINIMUM.value to minimumLoader,
            Keyword.MAXIMUM.value to maximumLoader,
            Keyword.EXCLUSIVE_MINIMUM.value to exclusiveMinimumLoader,
            Keyword.EXCLUSIVE_MAXIMUM.value to exclusiveMaximumLoader,
            Keyword.MULTIPLE_OF.value to multipleOfLoader,
            Keyword.ENUM.value to enumLoader,
            Keyword.DEPENDENT_REQUIRED.value to dependentRequiredLoader,
            Keyword.FORMAT.value to formatLoader,
            Keyword.PROPERTY_NAMES.value to propertyNamesLoader,
            Keyword.PATTERN.value to patternLoader,
            Keyword.DEPENDENT_SCHEMAS.value to dependentSchemasLoader,
            Keyword.IF.value to ifThenElseLoader,
            Keyword.CONTAINS.value to containsLoader,
            Keyword.PREFIX_ITEMS.value to prefixItemsLoader,
            Keyword.ITEMS.value to itemsSchemaLoader,
            Keyword.ONE_OF.value to oneOfLoader,
            Keyword.ANY_OF.value to anyOfLoader,
            Keyword.ALL_OF.value to allOfLoader,
            Keyword.UNIQUE_ITEMS.value to uniqueItemsLoader,
            Keyword.CONST.value to constLoader,
            Keyword.READ_ONLY.value to readOnlyLoader,
            Keyword.WRITE_ONLY.value to writeOnlyLoader
    )

    private constructor(
            schemaJson: IJsonValue,
            config: SchemaLoaderConfig,
            loadingState: LoadingState
    ) : this(schemaJson, config) {
        this.loadingState = loadingState
    }

    private fun <R> enterScope(json: IJsonValue, runnable: () -> R): R {
        val origBaseUri = loadingState.baseURI
        adjustBaseURI(json)
        try {
            return runnable()
        } finally {
            loadingState.baseURI = origBaseUri
        }
    }

    private var loadingState: LoadingState = LoadingState(schemaJson, baseURI = schemaJson.location.documentSource ?: config.initialBaseURI, vocabulary = findVocabulariesInMetaSchema(schemaJson))

    private fun findVocabulariesInMetaSchema(schemaJson: IJsonValue): List<String> {
        return when (schemaJson) {
            is IJsonBoolean -> emptyList()
            is IJsonObj -> {
                return schemaJson[Keyword.SCHEMA.value]
                    ?.requireString()
                    ?.let { config.schemaClient.getParsed(URI(it.value))
                        .requireObject()[Keyword.VOCABULARY.value]
                        ?.requireObject()?.properties
                        ?.filter { it.value.requireBoolean().value }
                        ?.keys
                        ?.map { it.requireString().value }
                        ?.toList()
                    } ?: emptyList()
            }
            else -> throw JsonTypingException("boolean or object",
                schemaJson.jsonTypeAsString(), schemaJson.location)
        }
    }

    operator fun invoke(): Schema = loadRootSchema()
    fun load(): Schema = loadRootSchema()

    private fun lookupAnchors(json: IJsonValue, baseURI: URI) {
        if (shouldStopAnchorLookup(json)) return
        when (json) {
            is IJsonObj -> {
                enterScope(json) {
                    when (val id = json[Keyword.ID.value]) {
                        is IJsonString -> {
                            // baseURI is already resolved to child ID
                            loadingState.registerRawSchemaByAnchor(loadingState.baseURI.toString(), json)
                        }
                    }
                    when (val anchor = json[Keyword.ANCHOR.value]) {
                        is IJsonString -> {
                            val resolvedAnchor = resolveAgainstBaseURI("#" + anchor.value)
                            loadingState.registerRawSchemaByAnchor(resolvedAnchor.toString(), json)
                        }
                    }
                    when (val anchor = json[Keyword.DYNAMIC_ANCHOR.value]) {
                        is IJsonString -> {
                            val resolvedAnchor = resolveAgainstBaseURI("#" + anchor.value)
                            loadingState.registerRawSchemaByDynAnchor(resolvedAnchor.toString(), json)
                        }
                    }
                    json.properties
                            .filter { (key, _) ->
                                key.value != Keyword.ENUM.value && key.value != Keyword.CONST.value
                            }
                            .forEach { (_, value) -> lookupAnchors(value, loadingState.baseURI) }
                }
            }
            is IJsonArray<*> -> {
                json.elements.forEach { lookupAnchors(it, loadingState.baseURI) }
            }
        }
    }

    private fun resolveRelativeURI(ref: String): URI {
        return try {
            val uri = URI(ref)
            if (uri.isAbsolute || config.additionalMappings.containsKey(parseUri(ref).toBeQueried)) {
                uri
            } else{
                resolveAgainstBaseURI(ref)
            }
        } catch (e: URISyntaxException) {
            resolveAgainstBaseURI(ref)
        }
    }

    private fun resolveAgainstBaseURI(ref: String): URI {
        if (loadingState.baseURI.toString().startsWith("urn:")) {
            return URI(loadingState.baseURI.toString() + ref)
        }
        return loadingState.baseURI.resolve(ref)
    }

    private fun createReferenceSchema(location: SourceLocation, ref: String): ReferenceSchema {
        var s: String
        try {
            s = resolveRelativeURI(ref).toString()
        } catch (e: java.lang.IllegalArgumentException) {
            s = loadingState.baseURI.toString() + ref
        }
        val anchor = loadingState.getAnchorByURI(s)
        return anchor.createReference(location, s)
    }

    /**
     * lookupAnchors() should not proceed with the recursion into values of unknown keywords
     */
    private fun shouldStopAnchorLookup(json: IJsonValue): Boolean {
        val locationSegments = json.location.pointer.segments
        if (locationSegments.isNotEmpty()) {
            if (!isKnownKeyword(locationSegments.last())) { // last segment is unknown keyword
                if (locationSegments.size == 1) {
                    return true
                }
                val beforeLastSegment = locationSegments[locationSegments.size - 2]
                val beforeLastKeyword = Keyword.entries.find { it.value == beforeLastSegment }
                if (beforeLastKeyword == null || !beforeLastKeyword.hasMapLikeSemantics) {
                    return true
                }
            }
        }
        return false
    }

    private fun isKnownKeyword(lastSegment: String) = Keyword.entries.any { lastSegment == it.value }

    private fun adjustBaseURI(json: IJsonValue) {
        when (json) {
            is IJsonObj -> {
                when (val id = json[Keyword.ID.value]) {
                    is IJsonString -> {
                        if (!loadingState.baseURI.toString().endsWith(id.value)) {
                            loadingState.baseURI = resolveAgainstBaseURI(id.value)
                        }
                    }
                }
            }
        }
    }

    private fun loadRootSchema(): Schema {
        lookupAnchors(schemaJson, loadingState.baseURI)
        val root = loadSchema()
        when (collectedExceptions.size) {
            0 ->  root;
            1 -> throw collectedExceptions[0]
            else -> throw AggregateSchemaLoadingException(collectedExceptions)
        }
        return root
    }

    private fun loadSchema(): Schema {
        adjustBaseURI(schemaJson)
        val finalRef = createReferenceSchema(schemaJson.location, "#")
        loadingState.registerRawSchemaByAnchor(loadingState.baseURI.toString(), schemaJson)

        do {
            val knot: Knot? = loadingState.nextLoadableAnchor()
            if (knot === null) {
                val unresolved: Knot? = loadingState.nextUnresolvedAnchor()
                if (unresolved === null) {
                    break
                }
                try {
                    val pair = resolve(unresolved.referenceSchemas[0])
                    unresolved.json = pair.first
                    unresolved.lexicalContextBaseURI = pair.second
                } catch (ex: SchemaLoadingException) {
                    loadingState.markUnreachable(unresolved)
                    collectedExceptions.add(ex)
                }
            } else {
                knot.underLoading = true

                val origBaseURI = loadingState.baseURI

                knot.lexicalContextBaseURI?.let {
                    loadingState.baseURI = it
                }
                val schema = doLoadSchema(knot.json!!)
                loadingState.baseURI = origBaseURI
                knot.resolveWith(schema)
                knot.underLoading = false
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
            continingRoot = config.schemaClient.getParsed(uri.toBeQueried)
            loadingState.registerRawSchemaByAnchor(uri.toBeQueried.toString(), continingRoot)

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
        return evaluateJsonPointer(continingRoot, uri.fragment, referenceSchema)
    }

    private fun evaluateJsonPointer(root: IJsonValue, pointer: String, ref: ReferenceSchema): Pair<IJsonValue, URI> {
        val segments = LinkedList(pointer.split("/"))
        if ("#" != segments.poll()) {
            throw Error("invalid json pointer: $pointer")
        }
        fun unescape(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8.name())
                .replace("~1", "/")
                .replace("~0", "~")

        fun lookupNext(root: IJsonValue, segments: LinkedList<String>): Pair<IJsonValue, URI> {
            return enterScope(root) {
                if (segments.isEmpty()) {
                    return@enterScope Pair(root, loadingState.baseURI)
                }
                val segment = unescape(segments.poll())
                when (root) {
                    is IJsonObject<*, *> -> {
                        val child = root[segment]
                        if (child === null) {
                            throw RefResolutionException(
                                ref = ref,
                                missingProperty = segment,
                                resolutionFailureLocation = root.location
                            )
                        }
                        return@enterScope lookupNext(child, segments)
                    }

                    is IJsonArray<*> -> {
                        try {
                            val child = root[Integer.parseInt(segment)]
                            return@enterScope lookupNext(child, segments)
                        } catch (ex: IndexOutOfBoundsException) {
                            throw RefResolutionException(
                                ref = ref,
                                missingProperty = segment,
                                resolutionFailureLocation = root.location
                            )
                        }
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
        val baseURIofRoot: URI = idKeywordValue?.let { URI(it) }
                ?: root.location.documentSource
                ?: DEFAULT_BASE_URI

        return runWithChangedBaseURI(baseURIofRoot) {
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
                    else -> throw JsonTypingException("boolean or object",
                        schemaJson.jsonTypeAsString(), schemaJson.location)
                }
        return retval
    }

    private val collectedExceptions = mutableListOf<SchemaLoadingException>()

    private fun createCompositeSchema(schemaJson: IJsonObject<*, *>): Schema {
        val subschemas = mutableSetOf<Schema>()
        var title: IJsonString? = null
        var description: IJsonString? = null
        var deprecated: IJsonBoolean? = null
        var default: IJsonValue? = null
        var dynamicRef: DynamicReference? = null
        var dynamicAnchor: String? = null
        var propertySchemas: Map<String, Schema> = emptyMap()
        var patternPropertySchemas: Map<Regexp, Schema> = emptyMap()
        var unevaluatedItemsSchema: Schema? = null
        var unevaluatedPropertiesSchema: Schema? = null
        val unprocessedProperties: MutableMap<IJsonString, IJsonValue> = mutableMapOf()
        var definedSubschemas: Map<IJsonString, Schema> = emptyMap()
        return enterScope(schemaJson) {
            schemaJson.properties.forEach { (name, value) ->
                try {
                    var subschema: Schema? = null
                    val ctx = LoadingContext(
                        schemaJson, value, name.location,
                        { loadChild(it) },
                        regexpFactory
                    )
                    when (name.value) {
                        Keyword.PROPERTIES.value -> propertySchemas = loadPropertySchemas(value.requireObject())
                        Keyword.PATTERN_PROPERTIES.value -> patternPropertySchemas =
                            loadPatternPropertySchemas(value.requireObject())
                        Keyword.REF.value -> subschema = createReferenceSchema(name.location, value.requireString().value)
                        Keyword.DYNAMIC_REF.value -> dynamicRef = DynamicReference(
                            ref = value.requireString().value,
                            fallbackReferredSchema = createReferenceSchema(
                                ref = value.requireString().value,
                                location = schemaJson.location
                            )
                        )
                        Keyword.DYNAMIC_ANCHOR.value -> dynamicAnchor = value.requireString().value
                        Keyword.TITLE.value -> title = value.requireString()
                        Keyword.DESCRIPTION.value -> description = value.requireString()
                        Keyword.DEPRECATED.value -> deprecated = value.requireBoolean()
                        Keyword.DEFAULT.value -> default = value
                        Keyword.UNEVALUATED_ITEMS.value -> unevaluatedItemsSchema =
                            UnevaluatedItemsSchema(loadChild(value), name.location)
                        Keyword.UNEVALUATED_PROPERTIES.value -> unevaluatedPropertiesSchema =
                            UnevaluatedPropertiesSchema(loadChild(value), name.location)
                        Keyword.DEFS.value -> definedSubschemas =
                            value.requireObject().properties.mapValues { loadChild(it.value) }
                    }
                    val loader = keywordLoaders[name.value]
                    if (subschema === null && loader != null) {
                        subschema = loader(ctx)
                    }
                    if (subschema != null) subschemas.add(subschema)
                    if (!isKnownKeyword(name.value)) {
                        unprocessedProperties[name] = value
                    }
                } catch (ex: JsonTypingException) {
                    collectedExceptions.add(JsonTypeMismatchException(ex))
                }
            }
            return@enterScope CompositeSchema(
                    subschemas = subschemas,
                    location = schemaJson.location,
                    title = title,
                    description = description,
                    deprecated = deprecated,
                    default = default,
                    propertySchemas = propertySchemas,
                    patternPropertySchemas = patternPropertySchemas,
                    dynamicRef = dynamicRef,
                    dynamicAnchor = dynamicAnchor,
                    unevaluatedItemsSchema = unevaluatedItemsSchema,
                    unevaluatedPropertiesSchema = unevaluatedPropertiesSchema,
                    unprocessedProperties = unprocessedProperties,
                    vocabulary = loadingState.vocabulary,
                    definedSubschemas = definedSubschemas.mapKeys { it.key.value }
            )
        }
    }

    private fun loadPatternPropertySchemas(obj: IJsonObject<*, *>): Map<Regexp, Schema>
        = obj.properties.map { regexpFactory.createHandler(it.key.value) to loadChild(it.value) }.toMap()

    private fun loadPropertySchemas(schemasMap: IJsonObject<*, *>): Map<String, Schema>
        = schemasMap.properties.map { it.key.value to loadChild(it.value) }.toMap()

    private fun loadChild(schemaJson: IJsonValue): Schema {
        val childLoader = SchemaLoader(schemaJson, config, loadingState)
        val child = childLoader.doLoadSchema(schemaJson)
        collectedExceptions.addAll(childLoader.collectedExceptions)
        return child
    }
}

internal fun arrayToSubschemaList(subschemas: IJsonArray<*>, subschemaLoader: (IJsonValue) -> Schema): List<Schema> =
    subschemas.elements.stream()
        .map { subschemaLoader(it) }
        .collect(toList())
