package net.corda.node.services.api

import java.io.InputStream

/**
 * A service that implements AcceptsFileUpload can have new binary data provided to it via an HTTP upload.
 *
 * TODO: In future, also accept uploads over the MQ interface too.
 */
interface AcceptsFileUpload {
    /** A string that prefixes the URLs, e.g. "attachments" or "interest-rates". Should be OK for URLs. */
    val dataTypePrefix: String

    /** What file extensions are acceptable for the file to be handed to upload() */
    val acceptableFileExtensions: List<String>

    /**
     * Accepts the data in the given input stream, and returns some sort of useful return message that will be sent
     * back to the user in the response.
     */
    fun upload(data: InputStream): String
}
