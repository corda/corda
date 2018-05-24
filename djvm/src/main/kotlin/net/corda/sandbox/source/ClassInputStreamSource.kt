package net.corda.sandbox.source

import java.io.InputStream

/**
 * Source of compiled Java classes.
 *
 * @property stream The stream representation of the class source, a JAR.
 */
class ClassInputStreamSource(
        private val stream: InputStream
) : ClassSource() {

    override fun iterator(): Iterator<InputStream> {
        return JarInputStreamIterator(stream)
    }

}