package com.github.erosb.jsonsKema

import org.yaml.snakeyaml.Yaml
import java.io.*
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

fun interface SchemaClient {
    fun get(uri: URI): InputStream

    /**
     * Fetches a raw json schema as string using {@code get()} and parses it into an {@code IJsonValue} instance.
     *
     * @throws SchemaDocumentLoadingException if an IO exception or a parsing exception occurs
     * @throws JsonDocumentLoadingException if a {@code JsonParseException} occurs
     * @throws YamlDocumentLoadingException if a {@code YamlParseException} occurs
     */
    fun getParsed(uri: URI): IJsonValue {
        try {
            val reader = BufferedReader(InputStreamReader(get(uri)))
            return parseStringIntoRawSchema(reader.readText(), uri)
        } catch (ex: UncheckedIOException) {
            throw SchemaDocumentLoadingException(uri, ex)
        } catch (ex: JsonParseException) {
            throw JsonDocumentLoadingException(uri, ex)
        } catch (ex: YamlParseException) {
            throw YamlDocumentLoadingException(uri, ex)
        }
    }
}

internal class DefaultSchemaClient : SchemaClient {

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


internal class ClassPathAwareSchemaClient(private val fallbackClient: SchemaClient) : SchemaClient {


    override fun get(url: URI): InputStream {
        val maybeString = handleProtocol(url.toString())
        return if (maybeString.isPresent) {
            val stream = loadFromClasspath(maybeString.get())
            stream ?: throw UncheckedIOException(IOException(String.format("Could not find %s", url)))
        } else {
            fallbackClient.get(url)
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


internal class MemoizingSchemaClient(private val delegate: SchemaClient) : SchemaClient {

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

internal class PrepopulatedSchemaClient(
    private val fallbackClient: SchemaClient,
    additionalMappings: Map<URI, String> = mapOf()
    ) : SchemaClient {

    private val mappings: Map<URI, String> = mapOf(
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
        return mappings[uri]
            ?.toByteArray(StandardCharsets.UTF_8)
            ?.let { ByteArrayInputStream(it) }
            ?: fallbackClient.get(uri)
    }

}
