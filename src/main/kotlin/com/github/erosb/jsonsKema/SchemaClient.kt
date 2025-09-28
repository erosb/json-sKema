package com.github.erosb.jsonsKema

import java.io.*
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

fun interface SchemaClient {

    companion object {

        /**
         * Returns a [SchemaClient] instance which is used by default by [SchemaLoader] (unless it overwritten by
         * passing an other instance to [SchemaLoaderConfig.schemaClient]).
         *
         * Directly calling this method can be useful in cases when a custom [SchemaClient] implementation is necessary, but
         * it has to use this default instance as a fallback mechanism in some scenario.
         *
         * The returned value (as of now) is a chain of `SchemaClient`s with fallbacks, as:
         * - the first in chain is a[MemoizingSchemaClient] (to avoid duplicate lookups)
         * - the second is a [PrepopulatedSchemaClient] ([additionalMappings] are passed to it)
         * - the third is a [ClassPathAwareSchemaClient], which is used to query schema documents from the classpath
         * - the last an [URLQueryingSchemaClient] which, if no previous lookup succeeded, converts the [URI] into  [URL], and
         * performs an actual network call.
         */
        @JvmStatic
        fun createDefaultInstance(additionalMappings: Map<URI, String>): SchemaClient = MemoizingSchemaClient(
            PrepopulatedSchemaClient(
                ClassPathAwareSchemaClient(URLQueryingSchemaClient()),
                additionalMappings
            )
        )
    }
    fun get(uri: URI): InputStream

    /**
     * Fetches a raw json schema as string using [get(URI)] and parses it into an [IJsonValue] instance.
     *
     * @throws SchemaDocumentLoadingException if an IO exception or a parsing exception occurs
     * @throws JsonDocumentLoadingException if a [JsonParseException] occurs
     * @throws YamlDocumentLoadingException if a [YamlParseException] occurs
     */
    fun getParsed(uri: URI): IJsonValue {
        try {
            val reader = BufferedReader(InputStreamReader(get(uri)))
            return reader.use { JsonValue.parse(it.readText(), uri) }
        } catch (ex: UncheckedIOException) {
            throw SchemaDocumentLoadingException(uri, ex)
        } catch (ex: JsonParseException) {
            throw JsonDocumentLoadingException(uri, ex)
        } catch (ex: YamlParseException) {
            throw YamlDocumentLoadingException(uri, ex)
        }
    }
}

/**
 * A [SchemaClient] which converts the [URI] into [URL] and attempts to make a network call.
 *
 * In case it receives a response containing a `Location` header, it will follow the redirect.
 */
class URLQueryingSchemaClient : SchemaClient {

    override fun get(uri: URI): InputStream {
        try {
            val u = toURL(uri)
            val conn = u.openConnection()
            val location = conn.getHeaderField("Location")
            return location?.let { get(URI(location)) } ?: conn.content as InputStream
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun toURL(uri: URI): URL {
        try {
            return uri.toURL()
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("URI '$uri' can't be converted to URL: ${e.message}", e);
        }
    }
}

/**
 * A [SchemaClient] which first checks if the URI scheme is `classpath`, and if yes, it attempts to load
 * the schema document from the classpath.
 *
 * For non-classpath URIs it delegates the call to its [fallbackClient].
 */
class ClassPathAwareSchemaClient(private val fallbackClient: SchemaClient) : SchemaClient {


    override fun get(uri: URI): InputStream {
        val maybeString = handleProtocol(uri.toString())
        return if (maybeString.isPresent) {
            val stream = loadFromClasspath(maybeString.get())
            stream ?: throw UncheckedIOException(IOException(String.format("Could not find %s", uri)))
        } else {
            fallbackClient.get(uri)
        }
    }

    private fun loadFromClasspath(str: String): InputStream? {
        return javaClass.getResourceAsStream(str)
    }

    private fun handleProtocol(url: String): Optional<String> {
        return HANDLED_PREFIXES.stream().filter { prefix: String? ->
            url.startsWith(
                prefix!!
            )
        }
            .map { prefix: String ->
                "/" + url.substring(
                    prefix.length
                )
            }
            .findFirst()
    }

    companion object {
        private val HANDLED_PREFIXES: List<String> = listOf("classpath://", "classpath:/", "classpath:")
    }
}


class MemoizingSchemaClient(private val delegate: SchemaClient) : SchemaClient {

    val cache: MutableMap<URI, ByteArray> = mutableMapOf()

    override fun get(uri: URI): InputStream = ByteArrayInputStream(
        cache.computeIfAbsent(uri) {
            val out = ByteArrayOutputStream()
            delegate.get(it).transferToOut(out)
            return@computeIfAbsent out.toByteArray()
        }
    )

}

private fun InputStream.transferToOut(out: OutputStream): Long {
    var transferred: Long = 0
    val bufferSize = 8192
    val buffer = ByteArray(bufferSize)
    var read: Int
    while ((this.read(buffer, 0, bufferSize).also { read = it }) >= 0) {
        out.write(buffer, 0, read)
        transferred += read.toLong()
    }
    return transferred
}

internal fun readFromClassPath(path: String): String =
    String(
        ByteArrayOutputStream().also {
            PrepopulatedSchemaClient::class.java.getResourceAsStream(path)!!.transferToOut(it)
        }.toByteArray()
    )

/**
 * A SchemaClient which, holds a registry of URI -> String mapping. If the URI received by {@code get()} is known by the registry,
 * then the mapped {@code String} is returned, otherwise the {@code fallbackClient} is used to obtain the content.
 *
 * The registry contains default mappings for meta-schema URIs, which can be extended by adding {@code additionalMappings}.
 */
class PrepopulatedSchemaClient(
    private val fallbackClient: SchemaClient,
    additionalMappings: Map<URI, String> = mapOf()
    ) : SchemaClient {

    private val registry: Map<URI, String> = mapOf(
        URI("https://json-schema.org/draft/2020-12/schema") to readFromClassPath("/json-meta-schemas/draft2020-12/schema.json"),
        URI("https://json-schema.org/draft/2020-12/meta/core") to readFromClassPath("/json-meta-schemas/draft2020-12/core.json"),
        URI("https://json-schema.org/draft/2020-12/meta/validation") to readFromClassPath("/json-meta-schemas/draft2020-12/validation.json"),
        URI("https://json-schema.org/draft/2020-12/meta/applicator") to readFromClassPath("/json-meta-schemas/draft2020-12/applicator.json"),
        URI("https://json-schema.org/draft/2020-12/meta/unevaluated") to readFromClassPath("/json-meta-schemas/draft2020-12/unevaluated.json"),
        URI("https://json-schema.org/draft/2020-12/meta/meta-data") to readFromClassPath("/json-meta-schemas/draft2020-12/meta-data.json"),
        URI("https://json-schema.org/draft/2020-12/meta/format-annotation") to readFromClassPath("/json-meta-schemas/draft2020-12/format-annotation.json"),
        URI("https://json-schema.org/draft/2020-12/meta/content") to readFromClassPath("/json-meta-schemas/draft2020-12/content.json")
    ) + additionalMappings

    override fun get(uri: URI): InputStream {
        return registry[uri]
            ?.toByteArray(StandardCharsets.UTF_8)
            ?.let { ByteArrayInputStream(it) }
            ?: fallbackClient.get(uri)
    }
}
