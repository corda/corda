package net.corda.sandbox.source

import java.io.IOException
import java.io.InputStream
import java.util.jar.JarInputStream

/**
 * Iterator for traversing over a JAR stream.
 *
 * @param inputStream The input stream representing the JAR.
 */
class JarInputStreamIterator(
        inputStream: InputStream
) : Iterator<InputStream>, AutoCloseable {

    private var jarInputStream: JarInputStream? = JarInputStream(inputStream)

    private var isDone = false

    /**
     * Check if there are more class entries in the traversed JAR stream.
     */
    override fun hasNext(): Boolean {
        if (isDone) {
            close()
            return false
        }
        while (true) {
            val entry = jarInputStream?.nextEntry
            if (entry == null) {
                isDone = true
                close()
                return false
            }
            if (!isClass(entry.name)) {
                continue
            }
            return true
        }
    }

    /**
     * Return the next class entry in the JAR stream, in the form of an input stream.
     */
    override fun next(): InputStream {
        return jarInputStream!!
    }

    /**
     * Close the JAR input stream when it has been traversed.
     */
    override fun close() {
        try {
            jarInputStream?.close()
        } catch (exception: IOException) {
            throw exception
        } finally {
            isDone = true
            jarInputStream = null

        }
    }

    /**
     * Check if path is referring to a class file.
     */
    private fun isClass(path: String) =
            path.endsWith(".class", true)

}
