package core.node

import core.Attachment
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.jar.JarEntry

class OverlappingAttachments : Exception()

/**
 * A custom ClassLoader for creating contracts distributed as attachments and for contracts to
 * access attachments.
 */
class AttachmentsClassLoader private constructor(val tmpFiles: List<File>)
: URLClassLoader(tmpFiles.map { URL("file", "", it.toString()) }.toTypedArray()), Closeable {

    override fun close() {
        super.close()

        for (file in tmpFiles) {
            file.delete()
        }
    }

    override fun loadClass(name: String?, resolve: Boolean): Class<*>? {
        return super.loadClass(name, resolve)
    }

    companion object {
        fun create(streams: List<Attachment>): AttachmentsClassLoader {

            validate(streams)

            var tmpFiles = streams.map {
                var filename = File.createTempFile("jar", "")
                it.open().use {
                    str ->
                    FileOutputStream(filename).use { str.copyTo(it) }
                }
                filename
            }

            return AttachmentsClassLoader(tmpFiles)
        }

        private fun validate(streams: List<Attachment>) {
            val set = HashSet<String>()

            val jars = streams.map { it.openAsJAR() }

            for (jar in jars) {

                var entry: JarEntry = jar.nextJarEntry ?: continue
                if (set.add(entry.name) == false) {
                    throw OverlappingAttachments()
                }
            }
        }

    }
}


