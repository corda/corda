/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.servlets

import core.node.services.StorageService
import core.utilities.loggerFor
import org.apache.commons.fileupload.servlet.ServletFileUpload
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class AttachmentUploadServlet : HttpServlet() {
    private val log = loggerFor<AttachmentUploadServlet>()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        @Suppress("DEPRECATION")    // Bogus warning due to superclass static method being deprecated.
        val isMultipart = ServletFileUpload.isMultipartContent(req)

        if (!isMultipart) {
            log.error("Got a non-file upload request to the attachments servlet")
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "This end point is for file uploads only.")
            return
        }

        val upload = ServletFileUpload()
        val iterator = upload.getItemIterator(req)
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (!item.name.endsWith(".jar")) {
                log.error("Attempted upload of a non-JAR attachment: mime=${item.contentType} filename=${item.name}")
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "${item.name}: Must be have a MIME type of application/java-archive and a filename ending in .jar")
                return
            }

            log.info("Receiving ${item.name}")

            val storage = servletContext.getAttribute("storage") as StorageService
            item.openStream().use {
                val id = storage.attachments.importAttachment(it)
                log.info("${item.name} successfully inserted into the attachment store with id $id")
            }
        }
    }
}
