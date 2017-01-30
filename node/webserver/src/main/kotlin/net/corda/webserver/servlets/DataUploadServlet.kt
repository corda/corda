package net.corda.node.webserver.servlets

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.loggerFor
import net.corda.node.internal.Node
import net.corda.node.services.api.AcceptsFileUpload
import org.apache.commons.fileupload.servlet.ServletFileUpload
import java.util.*
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Accepts binary streams, finds the right [AcceptsFileUpload] implementor and hands the stream off to it.
 */
class DataUploadServlet: HttpServlet() {
    private val log = loggerFor<DataUploadServlet>()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        @Suppress("DEPRECATION") // Bogus warning due to superclass static method being deprecated.
        val isMultipart = ServletFileUpload.isMultipartContent(req)
        val rpc = servletContext.getAttribute("rpc") as CordaRPCOps

        if (!isMultipart) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "This end point is for data uploads only.")
            return
        }

        val upload = ServletFileUpload()
        val iterator = upload.getItemIterator(req)
        val messages = ArrayList<String>()

        if (!iterator.hasNext()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Got an upload request with no files")
            return
        }

        while (iterator.hasNext()) {
            val item = iterator.next()
            log.info("Receiving ${item.name}")

            try {
                val dataType = req.pathInfo.substring(1).substringBefore('/')
                messages += rpc.uploadFile(dataType, item.name, item.openStream())
                log.info("${item.name} successfully accepted: ${messages.last()}")
            } catch(e: RuntimeException) {
                println(e)
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Got a file upload request for an unknown data type")
            }
        }

        // Send back the hashes as a convenience for the user.
        val writer = resp.writer
        messages.forEach { writer.println(it) }
    }
}
