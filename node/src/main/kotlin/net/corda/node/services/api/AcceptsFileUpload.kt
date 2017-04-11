package net.corda.node.services.api

import net.corda.core.node.services.FileUploader

/**
 * A service that implements AcceptsFileUpload can have new binary data provided to it via an HTTP upload.
 *
 * TODO: In future, also accept uploads over the MQ interface too.
 */
interface AcceptsFileUpload : FileUploader {
    /** A string that prefixes the URLs, e.g. "attachments" or "interest-rates". Should be OK for URLs. */
    val dataTypePrefix: String

    /** What file extensions are acceptable for the file to be handed to upload() */
    val acceptableFileExtensions: List<String>

    override fun accepts(type: String) = type == dataTypePrefix
}
