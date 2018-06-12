@file:JvmName("AsmTools")
package net.corda.gradle.jarfilter.asm

import net.corda.gradle.jarfilter.descriptor
import net.corda.gradle.jarfilter.toPathFormat
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import java.io.ByteArrayInputStream
import java.io.InputStream


fun ByteArray.accept(visitor: (ClassVisitor) -> ClassVisitor): ByteArray {
    return ClassWriter(COMPUTE_MAXS).let { writer ->
        ClassReader(this).accept(visitor(writer), 0)
        writer.toByteArray()
    }
}

private val String.resourceName: String get() = "$toPathFormat.class"
val Class<*>.resourceName get() = name.resourceName
val Class<*>.bytecode: ByteArray get() = classLoader.getResourceAsStream(resourceName).use { it.readBytes() }
val Class<*>.descriptor: String get() = name.descriptor

/**
 * Functions for converting bytecode into a "live" Java class.
 */
inline fun <reified T: R, reified R: Any> ByteArray.toClass(): Class<out R> = toClass(T::class.java, R::class.java)

fun <T: R, R: Any> ByteArray.toClass(type: Class<in T>, asType: Class<out R>): Class<out R>
    = BytecodeClassLoader(this, type.name, type.classLoader).createClass().asSubclass(asType)

private class BytecodeClassLoader(
    private val bytecode: ByteArray,
    private val className: String,
    parent: ClassLoader
) : ClassLoader(parent) {
    internal fun createClass(): Class<*> {
        return defineClass(className, bytecode, 0, bytecode.size).apply { resolveClass(this) }
    }

    // Ensure that the class we create also honours Class<*>.bytecode (above).
    override fun getResourceAsStream(name: String): InputStream? {
        return if (name == className.resourceName) ByteArrayInputStream(bytecode) else super.getResourceAsStream(name)
    }
}
