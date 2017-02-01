package net.corda.node.webserver.servlets

import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.StorageService
import net.corda.core.utilities.loggerFor
import java.io.FileNotFoundException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Allows the node administrator to either download full attachment zips, or individual files within those zips.
 *
 * GET /attachments/123abcdef12121            -> download the zip identified by this hash
 * GET /attachments/123abcdef12121/foo.txt    -> download that file specifically
 *
 * Files are always forced to be downloads, they may not be embedded into web pages for security reasons.
 *
 * TODO: See if there's a way to prevent access by JavaScript.
 * TODO: Provide an endpoint that exposes attachment file listings, to make attachments browseable.
 */
class AttachmentDownloadServlet : HttpServlet() {
    private val log = loggerFor<AttachmentDownloadServlet>()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val reqPath = req.pathInfo?.substring(1)
        if (reqPath == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST)
            return
        }

        try {
            val hash = SecureHash.parse(reqPath.substringBefore('/'))
            val storage = servletContext.getAttribute("storage") as StorageService
            val attachment = storage.attachments.openAttachment(hash) ?: throw FileNotFoundException()

            // Don't allow case sensitive matches inside the jar, it'd just be confusing.
            val subPath = reqPath.substringAfter('/', missingDelimiterValue = "").toLowerCase()

            resp.contentType = "application/octet-stream"
            if (subPath == "") {
                resp.addHeader("Content-Disposition", "attachment; filename=\"$hash.zip\"")
                attachment.open().use { it.copyTo(resp.outputStream) }
            } else {
                val filename = subPath.split('/').last()
                resp.addHeader("Content-Disposition", "attachment; filename=\"$filename\"")
                attachment.extractFile(subPath, resp.outputStream)
            }
            resp.outputStream.close()
        } catch(e: FileNotFoundException) {
            log.warn("404 Not Found whilst trying to handle attachment download request for ${servletContext.contextPath}/$reqPath")
            resp.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
    }
}
