package net.corda.webserver.servlets

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.loggerFor
import org.apache.commons.fileupload.servlet.ServletFileUpload
import java.io.IOException
import java.util.*
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Uploads to the node via the [CordaRPCOps] uploadFile interface.
 */
class DataUploadServlet : HttpServlet() {
    private val log = loggerFor<DataUploadServlet>()

    @Throws(IOException::class)
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
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
            val dataType = req.pathInfo.substring(1).substringBefore('/')
            if (dataType != "attachment") {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Got a file upload request for an unknown data type $dataType")
                return
            }
            messages += rpc.uploadAttachment(item.openStream()).toString()
            log.info("${item.name} successfully accepted: ${messages.last()}")
        }

        // Send back the hashes as a convenience for the user.
        val writer = resp.writer
        messages.forEach { writer.println(it) }
    }
}
