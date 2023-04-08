package com.github.erosb.jsonsKema

import java.io.*
import java.lang.IllegalArgumentException
import java.net.URI
import java.net.URL

fun interface SchemaClient {
    fun get(uri: URI): InputStream
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

internal class MemoizingSchemaClient(private val delegate: SchemaClient) : SchemaClient {

    val cache: MutableMap<URI, ByteArray> = mutableMapOf()

    override fun get(uri: URI): InputStream = ByteArrayInputStream(
        cache.computeIfAbsent(uri) {
            val out = ByteArrayOutputStream()
            delegate.get(it).transferTo(out)
            return@computeIfAbsent out.toByteArray()
        }
    )
}
