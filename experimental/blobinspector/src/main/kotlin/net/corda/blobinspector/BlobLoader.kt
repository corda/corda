package net.corda.blobinspector

import java.io.File
import java.net.URL

/**
 *
 */
class FileBlobHandler(config_: Config) : BlobHandler(config_) {
    private val path = File(URL((config_ as FileConfig).file).toURI())

    override fun getBytes(): ByteArray {
        return path.readBytes()
    }
}

/**
 *
 */
class InMemoryBlobHandler(config_: Config) : BlobHandler(config_) {
    private val localBytes = (config_ as InMemoryConfig).blob?.bytes ?: kotlin.ByteArray(0)
    override fun getBytes(): ByteArray = localBytes
}

/**
 *
 */
abstract class BlobHandler(val config: Config) {
    companion object {
        fun make(config: Config): BlobHandler {
            return when (config.mode) {
                Mode.file -> FileBlobHandler(config)
                Mode.inMem -> InMemoryBlobHandler(config)
            }
        }
    }

    abstract fun getBytes(): ByteArray
}

