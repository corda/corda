package net.corda.deterministic

import java.io.ByteArrayOutputStream
import java.io.IOException

private val classLoader: ClassLoader = object {}.javaClass.classLoader

@Throws(IOException::class)
fun bytesOfResource(resourceName: String): ByteArray {
    return ByteArrayOutputStream().let { baos ->
        classLoader.getResourceAsStream(resourceName).copyTo(baos)
        baos.toByteArray()
    }
}
