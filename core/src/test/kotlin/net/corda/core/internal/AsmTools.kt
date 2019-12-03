@file:JvmName("AsmTools")
package net.corda.core.internal

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.Opcodes.ASM7

val String.asInternalName: String get() = replace('.', '/')
val Class<*>.resourceName: String get() = "${name.asInternalName}.class"
val Class<*>.byteCode: ByteArray get() = classLoader.getResourceAsStream(resourceName)!!.use {
    it.readBytes()
}

fun Class<*>.renameTo(newName: String): ByteArray {
    return byteCode.accept { w -> RenamingWriter(newName, w) }
}

fun ByteArray.accept(visitor: (ClassVisitor) -> ClassVisitor): ByteArray {
    return ClassWriter(COMPUTE_MAXS).let { writer ->
        ClassReader(this).accept(visitor(writer), 0)
        writer.toByteArray()
    }
}

private class RenamingWriter(private val newName: String, visitor: ClassVisitor) : ClassVisitor(ASM7, visitor) {
    override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
        super.visit(version, access, newName, signature, superName, interfaces)
    }
}
