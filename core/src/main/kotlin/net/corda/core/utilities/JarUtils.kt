@file:JvmName("JarUtils")
package net.corda.core.utilities

import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.jar.JarInputStream

@Throws(IOException::class)
fun JarInputStream.extractFile(path: String, outputTo: OutputStream) {
    fun String.norm() = toLowerCase().split('\\', '/') // XXX: Should this really be locale-sensitive?
    val p = path.norm()
    while (true) {
        val e = nextJarEntry ?: break
        if (!e.isDirectory && e.name.norm() == p) {
            copyTo(outputTo)
            return
        }
        closeEntry()
    }
    throw FileNotFoundException(path)
}
