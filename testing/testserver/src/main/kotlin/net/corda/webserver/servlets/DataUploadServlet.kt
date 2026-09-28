package net.corda.webserver.servlets

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.contextLogger
import org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload
import java.io.IOException
import java.util.*

/**
 * Uploads to the node via the [CordaRPCOps] uploadFile interface.
 */
class DataUploadServlet : HttpServlet() {
    companion object {
        private val log = contextLogger()
    }

    @Throws(IOException::class)
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val isMultipart = JakartaServletFileUpload.isMultipartContent(req)
        val rpc = servletContext.getAttribute("rpc") as CordaRPCOps

        if (!isMultipart) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "This end point is for data uploads only.")
            return
        }

        val upload = JakartaServletFileUpload()
        val iterator = upload.getItemIterator(req)
        val messages = ArrayList<String>()

        if (!iterator.hasNext()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Got an upload request with no files")
            return
        }
        fun reportError(message: String) {
            println(message) // Show in webserver window.
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, message)
        }
        while (iterator.hasNext()) {
            val item = iterator.next()
            log.info("Receiving ${item.name}")
            val dataType = req.pathInfo.substring(1).substringBefore('/')
            if (dataType != "attachment") {
                reportError("Got a file upload request for an unknown data type $dataType")
                continue
            }
            try {
                messages += rpc.uploadAttachment(item.inputStream).toString()
            } catch (e: RuntimeException) {
                reportError(e.toString())
                continue
            }
            log.info("${item.name} successfully accepted: ${messages.last()}")
        }

        // Send back the hashes as a convenience for the user.
        val writer = resp.writer
        messages.forEach { writer.println(it) }
    }
}
