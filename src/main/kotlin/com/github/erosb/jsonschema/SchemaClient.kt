package com.github.erosb.jsonschema

import java.io.*
import java.net.URI


fun interface SchemaClient {
    fun get(uri: URI): InputStream
}

class DefaultSchemaClient : SchemaClient{
    
    override fun get(uri: URI): InputStream {
        try {
            val u = uri.toURL()
            val conn = u.openConnection()
            val location = conn.getHeaderField("Location")
            return location?.let { get(URI(location)) } ?: conn.content as InputStream
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

}
        
