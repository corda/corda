package net.corda.node.services.api

/**
 * A service that implements AcceptsFileUpload can have new binary data provided to it via an HTTP upload.
 */
// TODO This is no longer used and can be removed
interface AcceptsFileUpload : FileUploader {
    /** A string that prefixes the URLs, e.g. "attachments" or "interest-rates". Should be OK for URLs. */
    val dataTypePrefix: String

    /** What file extensions are acceptable for the file to be handed to upload() */
    val acceptableFileExtensions: List<String>

    override fun accepts(type: String) = type == dataTypePrefix
}
