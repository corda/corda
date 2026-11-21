package net.corda.core.serialization.internal

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

/**
 * Renames the references a class makes to the `javax.persistence` API (Corda 4.14 and earlier) to `jakarta.persistence` (Corda 4.15,
 * Hibernate 7), for the classes [AttachmentsClassLoader] loads from attachments. Only `jakarta.persistence` is on the classpath of a
 * Corda 4.15 node and of its external verifier, so a class of a contract attachment built for an earlier version that refers to a
 * `javax.persistence` type in its code (rather than in annotations alone, which the JVM drops if their types are missing) would fail to
 * link, and the transaction with it.
 *
 * This is the rename alone, and the one implementation of it: the node's CorDapp class loader converts the CorDapps it maps to the
 * database with `JavaxPersistenceTransformer` (`node-api`), which makes this rename and converts the Hibernate annotations on top
 * ([rename]'s `decorate` arguments). The Hibernate annotations of an attachment's class are left as they are, as nothing maps the classes
 * of an attachment class loader to a database.
 */
object JavaxPersistenceRenamer {
    const val JAVAX_PERSISTENCE = "javax/persistence/"
    const val JAKARTA_PERSISTENCE = "jakarta/persistence/"
    private const val JAVAX_PERSISTENCE_PACKAGE = "javax.persistence."
    private const val KOTLIN_METADATA = "Lkotlin/Metadata;"
    private const val KOTLIN_METADATA_STRINGS = "d2"

    /**
     * Whether the bytes of a class file refer to a `javax.persistence` class without also referring to a `jakarta.persistence` one
     * (constant pool entries are ASCII-compatible). A class that refers to both is annotated for both persistence APIs on purpose.
     */
    fun isBuiltForJavaxPersistenceOnly(classBytes: ByteArray): Boolean {
        val text = String(classBytes, Charsets.ISO_8859_1)
        return text.contains(JAVAX_PERSISTENCE) && !text.contains(JAKARTA_PERSISTENCE)
    }

    /**
     * Whether the class [className] of an attachment, given its class file, is to be converted: it is built for `javax.persistence`
     * alone, and is not itself a class of the `javax.persistence` API that the attachment bundles, which keeps its name.
     */
    fun isConverted(className: String, classBytes: ByteArray): Boolean {
        return !className.startsWith(JAVAX_PERSISTENCE_PACKAGE) && isBuiltForJavaxPersistenceOnly(classBytes)
    }

    /** The class with every reference to `javax.persistence` renamed to `jakarta.persistence`. */
    fun toJakartaPersistence(classBytes: ByteArray): ByteArray = rename(classBytes, JAVAX_PERSISTENCE, JAKARTA_PERSISTENCE)

    /**
     * The bytes of the class with every reference to the package [from] renamed to [to] (both in internal form, with a trailing slash):
     * types, descriptors, signatures, annotation values, invokedynamic arguments and the Kotlin metadata string table, which Kotlin
     * reflection reads the signatures of the class's members from. [decorate] wraps the visitor chain with any further rewriting of the
     * original class; [decorateOutput] wraps the writer, so sees the renamed class.
     */
    @JvmOverloads
    fun rename(
            classBytes: ByteArray,
            from: String,
            to: String,
            decorate: (ClassVisitor) -> ClassVisitor = { it },
            decorateOutput: (ClassVisitor) -> ClassVisitor = { it }
    ): ByteArray {
        val reader = ClassReader(classBytes)
        // A fresh writer, so that the constant pool of the result does not keep the entries of the original. The rename does not change
        // the code in length or in shape, so the frames and the maximums of the original still hold.
        val writer = ClassWriter(0)
        reader.accept(decorate(KotlinMetadataRenamer(from, to, ClassRemapper(decorateOutput(writer), PackageRemapper(from, to)))), 0)
        return writer.toByteArray()
    }

    /** Maps the internal names of the package [from] to the package [to] (both with a trailing slash). */
    class PackageRemapper(private val from: String, private val to: String) : Remapper(Opcodes.ASM9) {
        override fun map(internalName: String): String {
            return if (internalName.startsWith(from)) to + internalName.substring(from.length) else internalName
        }
    }

    /**
     * Renames the package in the string table (`d2`) of a Kotlin class's `@kotlin.Metadata`, whose entries name the types of the
     * class's members as JVM descriptors and internal names, which ASM's remapper does not see. The Kotlin compiler writes those
     * entries whole (it does not use the substring form of the string table), so a plain replacement keeps them consistent.
     */
    private class KotlinMetadataRenamer(private val from: String, private val to: String, next: ClassVisitor) : ClassVisitor(Opcodes.ASM9, next) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            val next = super.visitAnnotation(descriptor, visible) ?: return null
            if (descriptor != KOTLIN_METADATA) return next
            return object : AnnotationVisitor(Opcodes.ASM9, next) {
                override fun visitArray(name: String?): AnnotationVisitor? {
                    val array = super.visitArray(name) ?: return null
                    if (name != KOTLIN_METADATA_STRINGS) return array
                    return object : AnnotationVisitor(Opcodes.ASM9, array) {
                        override fun visit(name: String?, value: Any?) {
                            super.visit(name, if (value is String) value.replace(from, to) else value)
                        }
                    }
                }
            }
        }
    }
}
