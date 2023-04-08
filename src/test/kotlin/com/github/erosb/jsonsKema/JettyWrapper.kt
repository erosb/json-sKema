package com.github.erosb.jsonsKema

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.io.*
import java.lang.Exception
import java.util.*
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

internal class IssueServlet(documentRoot: String) : HttpServlet() {
    private val documentRoot: String
    private fun openStream(pathInfo: String): InputStream? {
        return try {
            val stream = javaClass.getResourceAsStream(documentRoot + pathInfo)
            if (stream == null) {
                val file = File(documentRoot + pathInfo)
                if (file.exists()) {
                    return FileInputStream(file)
                }
            }
            stream
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        println("GET " + req.pathInfo)
        try {
            resp.contentType = "application/json"
            val stream = openStream(req.pathInfo)
            if (stream == null) {
                resp.status = 404
                return
            }
            BufferedReader(
                InputStreamReader(stream)
            ).use { bis ->
                var line: String?
                while (bis.readLine().also { line = it } != null) {
                    resp.writer.write(line)
                }
            }
        } catch (e: FileNotFoundException) {
            resp.status = 404
        } catch (e: RuntimeException) {
            e.printStackTrace()
            throw e
        }
    }

    companion object {
        private const val serialVersionUID = -951266179406031349L
    }

    init {
        this.documentRoot = Objects.requireNonNull(documentRoot, "documentRoot cannot be null")
    }
}

internal class JettyWrapper(documentRootPath: String) {
    private val server: Server
    fun start() {
        try {
            server.start()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun stop() {
        try {
            server.stop()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    init {
        server = Server(1234)
        val handler = ServletHandler()
        server.handler = handler
        handler.addServletWithMapping(ServletHolder(IssueServlet(documentRootPath)), "/*")
        try {
            server.start()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
