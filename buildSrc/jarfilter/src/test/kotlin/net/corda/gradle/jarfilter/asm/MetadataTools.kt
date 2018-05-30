@file:JvmName("MetadataTools")
package net.corda.gradle.jarfilter.asm

import net.corda.gradle.jarfilter.StdOutLogging
import org.jetbrains.kotlin.load.java.JvmAnnotationNames.*
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ASM6

/**
 * Rewrite the bytecode for this class with the Kotlin @Metadata of another class.
 */
inline fun <reified T: Any, reified X: Any> recodeMetadataFor(): ByteArray = T::class.java.metadataAs(X::class.java)

fun <T: Any, X: Any> Class<in T>.metadataAs(template: Class<in X>): ByteArray {
    val metadata = template.readMetadata().let { m ->
        val templateDescriptor = template.descriptor
        Pair(m.first, m.second.map { s -> if (s == templateDescriptor) descriptor else s }.toList())
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

private fun Class<*>.readMetadata(): Pair<List<String>, List<String>> {
    return MetadataReader().let { visitor ->
        ClassReader(bytecode).accept(visitor, 0)
        visitor.metadata
    }
}

private class MetadataReader : ClassVisitor(ASM6) {
    private val kotlinMetadata: MutableMap<String, List<String>> = mutableMapOf()
    val metadata: Pair<List<String>, List<String>> get() = Pair(
        kotlinMetadata[METADATA_DATA_FIELD_NAME] ?: emptyList(),
        kotlinMetadata[METADATA_STRINGS_FIELD_NAME] ?: emptyList()
    )

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        return if (descriptor == METADATA_DESC)
            KotlinMetadataReader()
        else
            super.visitAnnotation(descriptor, visible)
    }

    private inner class KotlinMetadataReader : AnnotationVisitor(api) {
        override fun visitArray(name: String): AnnotationVisitor? {
            return if (kotlinMetadata.containsKey(name))
                super.visitArray(name)
            else
                ArrayAccumulator(name)
        }

        private inner class ArrayAccumulator(private val name: String) : AnnotationVisitor(api) {
            private val data: MutableList<String> = mutableListOf()

            override fun visit(name: String?, value: Any?) {
                super.visit(name, value)
                data.add(value as String)
            }

            override fun visitEnd() {
                super.visitEnd()
                kotlinMetadata[name] = data
            }
        }
    }
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
