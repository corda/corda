/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.servlets

import core.node.AcceptsFileUpload
import core.node.Node
import core.utilities.loggerFor
import org.apache.commons.fileupload.servlet.ServletFileUpload
import java.util.*
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Accepts binary streams, finds the right [AcceptsFileUpload] implementor and hands the stream off to it.
 */
class DataUploadServlet : HttpServlet() {
    private val log = loggerFor<DataUploadServlet>()

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val node = servletContext.getAttribute("node") as Node

        @Suppress("DEPRECATION")    // Bogus warning due to superclass static method being deprecated.
        val isMultipart = ServletFileUpload.isMultipartContent(req)

        if (!isMultipart) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "This end point is for data uploads only.")
            return
        }

        val acceptor: AcceptsFileUpload? = findAcceptor(node, req)
        if (acceptor == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Got a file upload request for an unknown data type")
            return
        }

        val upload = ServletFileUpload()
        val iterator = upload.getItemIterator(req)
        val messages = ArrayList<String>()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (!acceptor.acceptableFileExtensions.any { item.name.endsWith(it) }) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "${item.name}: Must be have a filename ending in one of: ${acceptor.acceptableFileExtensions}")
                return
            }

            log.info("Receiving ${item.name}")

            item.openStream().use {
                val message = acceptor.upload(it)
                log.info("${item.name} successfully accepted: $message")
                messages += message
            }
        }

        // Send back the hashes as a convenience for the user.
        val writer = resp.writer
        messages.forEach { writer.println(it) }
    }

    private fun findAcceptor(node: Node, req: HttpServletRequest): AcceptsFileUpload? {
        return node.servicesThatAcceptUploads.firstOrNull { req.pathInfo.substring(1).substringBefore('/') == it.dataTypePrefix }
    }
}
