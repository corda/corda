@file:JvmName("MetadataTools")
package net.corda.gradle.jarfilter.asm

import net.corda.gradle.jarfilter.StdOutLogging
import org.jetbrains.kotlin.load.java.JvmAnnotationNames.*
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ASM6

@Suppress("UNCHECKED_CAST")
private val metadataClass: Class<out Annotation>
                = object {}.javaClass.classLoader.loadClass("kotlin.Metadata") as Class<out Annotation>

/**
 * Rewrite the bytecode for this class with the Kotlin @Metadata of another class.
 */
inline fun <reified T: Any, reified X: Any> recodeMetadataFor(): ByteArray = T::class.java.metadataAs(X::class.java)

fun <T: Any, X: Any> Class<in T>.metadataAs(template: Class<in X>): ByteArray {
    val metadata = template.readMetadata().let { m ->
        val templateDescriptor = template.descriptor
        val templatePrefix = templateDescriptor.dropLast(1) + '$'
        val targetDescriptor = descriptor
        val targetPrefix = targetDescriptor.dropLast(1) + '$'
        Pair(m.first, m.second.map { s ->
            when {
                // Replace any references to the template class with the target class.
                s == templateDescriptor -> targetDescriptor
                s.startsWith(templatePrefix) -> targetPrefix + s.substring(templatePrefix.length)
                else -> s
            }
        }.toList())
    }
    return bytecode.accept { w -> MetadataWriter(metadata, w) }
}

/**
 * Kotlin reflection only supports classes atm, so use this to examine file metadata.
 */
internal val Class<*>.fileMetadata: FileMetadata get() {
    val (d1, d2) = readMetadata()
    return FileMetadata(StdOutLogging(kotlin), d1, d2)
}

/**
 * For accessing the parts of class metadata that Kotlin reflection cannot reach.
 */
internal val Class<*>.classMetadata: ClassMetadata get() {
    val (d1, d2) = readMetadata()
    return ClassMetadata(StdOutLogging(kotlin), d1, d2)
}

private fun Class<*>.readMetadata(): Pair<List<String>, List<String>> {
    val metadata = getAnnotation(metadataClass)
    val d1 = metadataClass.getMethod(METADATA_DATA_FIELD_NAME)
    val d2 = metadataClass.getMethod(METADATA_STRINGS_FIELD_NAME)
    return Pair(d1.invoke(metadata).asList(), d2.invoke(metadata).asList())
}

@Suppress("UNCHECKED_CAST")
fun <T> Any.asList(): List<T> {
    return (this as? Array<T>)?.toList() ?: emptyList()
}

private class MetadataWriter(metadata: Pair<List<String>, List<String>>, visitor: ClassVisitor) : ClassVisitor(ASM6, visitor) {
    private val kotlinMetadata: MutableMap<String, List<String>> = mutableMapOf(
        METADATA_DATA_FIELD_NAME to metadata.first,
        METADATA_STRINGS_FIELD_NAME to metadata.second
    )

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        val av = super.visitAnnotation(descriptor, visible) ?: return null
        return if (descriptor == METADATA_DESC) KotlinMetadataWriter(av) else av
    }

    private inner class KotlinMetadataWriter(av: AnnotationVisitor) : AnnotationVisitor(api, av) {
        override fun visitArray(name: String): AnnotationVisitor? {
            val av = super.visitArray(name)
            if (av != null) {
                val data = kotlinMetadata.remove(name) ?: return av
                data.forEach { av.visit(null, it) }
                av.visitEnd()
            }
            return null
        }
    }
}
