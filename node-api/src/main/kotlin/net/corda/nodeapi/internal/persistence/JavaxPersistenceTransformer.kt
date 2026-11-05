package net.corda.nodeapi.internal.persistence

import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.persistence.factory.CordaSqlTypes
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.sql.Types

/**
 * Converts a class that was built against Java Persistence (`javax.persistence`, i.e. a CorDapp built for Corda 4.14 or earlier) to
 * Jakarta Persistence (`jakarta.persistence`), which is what Hibernate 7 reads, by renaming the package in the bytes of the class as
 * it is loaded. This is what the application servers did for Java EE applications when they moved to Jakarta EE, and it lets such a
 * CorDapp run on this node without being rebuilt: the rename covers the annotations of the schema classes (nested annotations and
 * enum values included), `AttributeConverter` implementations, code that uses the `EntityManager`, `Query` and criteria types in
 * `ServiceHub.withEntityManager` blocks (lambdas included) and `catch` clauses of the JPA exception types.
 *
 * The rename also covers the strings of a Kotlin class's `@kotlin.Metadata` (its `d2` string table), which name the types of its
 * members in the same form, so that Kotlin reflection on a converted class sees the renamed types too.
 *
 * Only a class that refers to `javax.persistence` and not to `jakarta.persistence` is converted (see [isBuiltForJavaxPersistenceOnly]).
 * A class that carries both sets of annotations, i.e. a CorDapp built to run on Corda 4.14 and 4.15 alike, must be left alone, as the
 * rename would duplicate its annotations.
 *
 * What cannot be renamed is Hibernate's own `@Type(type = "...")` annotation, whose members changed in Hibernate 6: the type names Corda
 * registered or documented for Hibernate 5 (`uuid-char`, `corda-wrapper-binary`, `corda-blob`, `materialized_blob`) and a few standard
 * Hibernate 5 names with a fixed JDBC type (`uuid-binary`, `binary`, `text`) are replaced by the `@JdbcTypeCode` Corda's own schemas use
 * on Hibernate 7 (see [substitutedHibernate5Types]); any other one makes the conversion fail with a message naming the class and the
 * member (such a CorDapp has to be rebuilt). Hibernate classes other than annotations (the `Session` API, the type classes) are not
 * converted either, and most of them changed between Hibernate 5 and 7; a converted class that refers to them is reported by
 * [referencesHibernateBeyondAnnotations] so that the class loader can warn about it, as such code fails when it runs.
 */
object JavaxPersistenceTransformer {
    private val logger = loggerFor<JavaxPersistenceTransformer>()

    private const val JAVAX_PERSISTENCE = "javax/persistence/"
    private const val JAKARTA_PERSISTENCE = "jakarta/persistence/"
    private const val HIBERNATE = "org/hibernate/"
    private const val HIBERNATE_ANNOTATIONS = "org/hibernate/annotations/"
    private const val HIBERNATE_TYPE = "Lorg/hibernate/annotations/Type;"
    private const val HIBERNATE_TYPE_DEF = "Lorg/hibernate/annotations/TypeDef;"
    private const val HIBERNATE_TYPE_DEFS = "Lorg/hibernate/annotations/TypeDefs;"
    private const val HIBERNATE_JDBC_TYPE_CODE = "Lorg/hibernate/annotations/JdbcTypeCode;"
    private const val KOTLIN_METADATA = "Lkotlin/Metadata;"
    private const val KOTLIN_METADATA_STRINGS = "d2"

    /**
     * The Hibernate 5 type names the node substitutes, with the JDBC type code of their Hibernate 7 mapping. The first four are the names
     * Corda 4.14 registered or documented (`BaseSessionFactoryFactory` registered `corda-wrapper-binary`, `corda-blob` and its own
     * `materialized_blob`, and its schemas used `uuid-char`); the others are standard Hibernate 5 names whose JDBC type is fixed. Each
     * mapping stores the same database format as the Hibernate 5 type did under Corda's Hibernate 7 compatibility settings.
     */
    val substitutedHibernate5Types: Map<String, Int> = mapOf(
            "uuid-char" to Types.VARCHAR,
            "corda-wrapper-binary" to Types.VARBINARY,
            // A BLOB, except bytea on PostgreSQL: the node's own blob columns use the same code (CordaTypeContributor).
            "corda-blob" to CordaSqlTypes.CORDA_BLOB,
            "materialized_blob" to Types.BLOB,
            // The default Hibernate 7 mapping of a UUID under hibernate.type.preferred_uuid_jdbc_type=BINARY.
            "uuid-binary" to Types.BINARY,
            "binary" to Types.VARBINARY,
            "text" to Types.LONGVARCHAR
    )

    /**
     * Whether the bytes of a class file refer to a `javax.persistence` class without also referring to a `jakarta.persistence` one
     * (constant pool entries are ASCII-compatible). A class that refers to both is annotated for both persistence APIs on purpose.
     */
    fun isBuiltForJavaxPersistenceOnly(classBytes: ByteArray): Boolean {
        val text = String(classBytes, Charsets.ISO_8859_1)
        return text.contains(JAVAX_PERSISTENCE) && !text.contains(JAKARTA_PERSISTENCE)
    }

    /**
     * Whether the bytes of a class file refer to a Hibernate class that is not an annotation, such as `org.hibernate.Session` or a
     * Hibernate 5 type class. The conversion leaves such references alone, and a class built for Hibernate 5 that uses them fails when
     * that code runs on Hibernate 7 (`Session.save` no longer exists, the type classes moved), so the class loader warns about it.
     */
    fun referencesHibernateBeyondAnnotations(classBytes: ByteArray): Boolean {
        val text = String(classBytes, Charsets.ISO_8859_1)
        var index = text.indexOf(HIBERNATE)
        while (index >= 0) {
            if (!text.startsWith(HIBERNATE_ANNOTATIONS, index)) return true
            index = text.indexOf(HIBERNATE, index + HIBERNATE.length)
        }
        return false
    }

    /**
     * The bytes of the class with every reference to `javax.persistence` renamed to `jakarta.persistence` and the Hibernate 5 type
     * annotations substituted. [className] is only used in log and error messages.
     *
     * @throws JavaxPersistenceConversionError if the class uses a Hibernate 5 annotation that has no substitute.
     */
    fun transform(classBytes: ByteArray, className: String): ByteArray {
        val converted = rename(classBytes, JAVAX_PERSISTENCE, JAKARTA_PERSISTENCE) { HibernateTypeRewriter(className, it) }
        logger.debug { "Converted $className from javax.persistence to jakarta.persistence" }
        return converted
    }

    /**
     * The bytes of the class with every reference to the package [from] renamed to [to] (both in internal form, with a trailing slash):
     * types, descriptors, signatures, annotation values, invokedynamic arguments and the Kotlin metadata string table. [decorate] wraps
     * the visitor chain with any further rewriting. Exposed for tests, which use it in the opposite direction to produce classes built
     * for `javax.persistence` from classes built for `jakarta.persistence`.
     */
    internal fun rename(classBytes: ByteArray, from: String, to: String, decorate: (ClassVisitor) -> ClassVisitor = { it }): ByteArray {
        val reader = ClassReader(classBytes)
        // A fresh writer, so that the constant pool of the result does not keep the entries of the original.
        val writer = ClassWriter(0)
        reader.accept(decorate(KotlinMetadataRenamer(from, to, ClassRemapper(writer, PackageRemapper(from, to)))), 0)
        return writer.toByteArray()
    }

    private class PackageRemapper(private val from: String, private val to: String) : Remapper(Opcodes.ASM9) {
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

    /**
     * Replaces `@Type(type = "<name>")` on fields and methods by `@JdbcTypeCode(<code>)` for the type names in
     * [substitutedHibernate5Types], and fails for any other Hibernate 5 type annotation.
     */
    private class HibernateTypeRewriter(private val className: String, next: ClassVisitor) : ClassVisitor(Opcodes.ASM9, next) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (descriptor == HIBERNATE_TYPE_DEF || descriptor == HIBERNATE_TYPE_DEFS) {
                throw JavaxPersistenceConversionError("The class $className uses the Hibernate 5 annotation @TypeDef, which no longer " +
                        "exists in Hibernate 7. $REBUILD_ADVICE")
            }
            return super.visitAnnotation(descriptor, visible)
        }

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            val fieldVisitor = super.visitField(access, name, descriptor, signature, value) ?: return null
            return object : FieldVisitor(Opcodes.ASM9, fieldVisitor) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                    return if (descriptor == HIBERNATE_TYPE) typeSubstitution(name, fieldVisitor::visitAnnotation) else super.visitAnnotation(descriptor, visible)
                }
            }
        }

        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
            return object : MethodVisitor(Opcodes.ASM9, methodVisitor) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                    return if (descriptor == HIBERNATE_TYPE) typeSubstitution(name, methodVisitor::visitAnnotation) else super.visitAnnotation(descriptor, visible)
                }
            }
        }

        /** Consumes a `@Type` annotation and, once its `type` is known, emits the substitute through [emit] (descriptor, visible). */
        private fun typeSubstitution(memberName: String, emit: (String, Boolean) -> AnnotationVisitor?): AnnotationVisitor {
            return object : AnnotationVisitor(Opcodes.ASM9) {
                private var typeName: String? = null

                override fun visit(name: String?, value: Any?) {
                    if (name == "type") typeName = value as? String
                }

                override fun visitEnd() {
                    val jdbcTypeCode = substitutedHibernate5Types[typeName]
                            ?: throw JavaxPersistenceConversionError("The class $className uses the Hibernate 5 annotation " +
                                    "@Type(type = \"$typeName\") on $memberName, which has no equivalent in Hibernate 7 that the node can " +
                                    "substitute (it substitutes ${substitutedHibernate5Types.keys}). $REBUILD_ADVICE")
                    emit(HIBERNATE_JDBC_TYPE_CODE, true)?.apply {
                        visit("value", jdbcTypeCode)
                        visitEnd()
                    }
                    logger.debug { "Replaced @Type(type = \"$typeName\") on $className.$memberName by @JdbcTypeCode($jdbcTypeCode)" }
                }
            }
        }
    }

    private const val REBUILD_ADVICE = "The CorDapp that contains the class needs to be rebuilt for Corda 4.15 - see the " +
            "\"Upgrading to Hibernate 7\" documentation."
}

/**
 * Thrown when a class built for Corda 4.14 or earlier cannot be converted for Hibernate 7. A [LinkageError] rather than an exception,
 * so that it is not swallowed by the code that loads CorDapp classes defensively and the node fails to start with the message instead.
 */
class JavaxPersistenceConversionError(message: String) : LinkageError(message)
