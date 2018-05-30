@file:JvmName("AsmTools")
package net.corda.gradle.jarfilter.asm

import net.corda.gradle.jarfilter.descriptor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS


fun ByteArray.accept(visitor: (ClassVisitor) -> ClassVisitor): ByteArray {
    return ClassWriter(COMPUTE_MAXS).let { writer ->
        ClassReader(this).accept(visitor(writer), 0)
        writer.toByteArray()
    }
}

private val String.resourceName: String get() = "${replace('.', '/')}.class"
val Class<*>.resourceName get() = name.resourceName
val Class<*>.bytecode: ByteArray get() = classLoader.getResourceAsStream(resourceName).use { it.readBytes() }
val Class<*>.descriptor: String get() = name.descriptor

/**
 * Functions for converting bytecode into a "live" Java class.
 */
inline fun <reified T: R, reified R: Any> ByteArray.toClass(): Class<out R> = toClass(T::class.java, R::class.java)

fun <T: R, R: Any> ByteArray.toClass(type: Class<in T>, asType: Class<out R>): Class<out R>
    = BytecodeClassLoader(this, type.classLoader).loadBytecodeAs(type.name).asSubclass(asType)

private class BytecodeClassLoader(private val bytecode: ByteArray, parent: ClassLoader) : ClassLoader(parent) {
    internal fun loadBytecodeAs(name: String): Class<*> {
        return defineClass(name, bytecode, 0, bytecode.size).apply { resolveClass(this) }
    }
}
