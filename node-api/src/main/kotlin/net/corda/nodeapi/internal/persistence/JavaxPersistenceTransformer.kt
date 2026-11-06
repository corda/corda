package net.corda.nodeapi.internal.persistence

import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.persistence.factory.CordaSqlTypes
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
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
 * converted either, and many of them changed between Hibernate 5 and 7. Given the class loader of the node's Hibernate, [transform]
 * resolves every Hibernate class and member the converted class refers to (supertypes, field and method types, calls, field accesses,
 * casts, `catch` clauses, method handles) and fails, naming them, if any does not exist in Hibernate 7: such code would fail with a
 * `NoSuchMethodError` or `NoClassDefFoundError` when it runs, and the node refuses to start instead. References to classes and members
 * that still exist (the exception types, `Session.flush`) pass, as the code runs against them unchanged. Annotations are not resolved:
 * the JVM drops an annotation whose class is missing, as it does for a CorDapp built for 4.15.
 */
object JavaxPersistenceTransformer {
    private val logger = loggerFor<JavaxPersistenceTransformer>()

    private const val JAVAX_PERSISTENCE = "javax/persistence/"
    private const val JAKARTA_PERSISTENCE = "jakarta/persistence/"
    private const val HIBERNATE = "org/hibernate/"
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
     * The bytes of the class with every reference to `javax.persistence` renamed to `jakarta.persistence` and the Hibernate 5 type
     * annotations substituted. [className] is only used in log and error messages. With [hibernateClassLoader], the class loader that
     * has the node's Hibernate 7 on its classpath, every Hibernate class and member the converted class refers to is resolved there,
     * except classes for which [bundledClass] is true (given their internal name): those the CorDapp ships itself, such as a Hibernate
     * library the node does not have (Hibernate Validator), and they are converted and checked on their own when they are loaded.
     *
     * @throws JavaxPersistenceConversionError if the class uses a Hibernate 5 annotation that has no substitute, or refers to a Hibernate
     * class or member that does not exist in Hibernate 7.
     */
    fun transform(
            classBytes: ByteArray,
            className: String,
            hibernateClassLoader: ClassLoader? = null,
            bundledClass: (internalName: String) -> Boolean = { false }
    ): ByteArray {
        val resolver = hibernateClassLoader?.let { HibernateResolver(it, bundledClass) }
        val converted = rename(classBytes, JAVAX_PERSISTENCE, JAKARTA_PERSISTENCE,
                decorate = { HibernateTypeRewriter(className, it) },
                decorateOutput = { if (resolver != null) HibernateReferenceChecker(resolver, it) else it })
        if (resolver != null && resolver.missing.isNotEmpty()) {
            throw JavaxPersistenceConversionError("The class $className uses Hibernate classes or members that do not exist in Hibernate 7 " +
                    "(Corda 4.15), so it would fail when that code runs: ${resolver.missing.joinToString("; ")}. $REBUILD_ADVICE")
        }
        logger.debug { "Converted $className from javax.persistence to jakarta.persistence" }
        return converted
    }

    /**
     * The bytes of the class with every reference to the package [from] renamed to [to] (both in internal form, with a trailing slash):
     * types, descriptors, signatures, annotation values, invokedynamic arguments and the Kotlin metadata string table. [decorate] wraps
     * the visitor chain with any further rewriting of the original class; [decorateOutput] wraps the writer, so sees the renamed class.
     * Exposed for tests, which use it in the opposite direction to produce classes built for `javax.persistence` from classes built for
     * `jakarta.persistence`.
     */
    internal fun rename(
            classBytes: ByteArray,
            from: String,
            to: String,
            decorate: (ClassVisitor) -> ClassVisitor = { it },
            decorateOutput: (ClassVisitor) -> ClassVisitor = { it }
    ): ByteArray {
        val reader = ClassReader(classBytes)
        // A fresh writer, so that the constant pool of the result does not keep the entries of the original.
        val writer = ClassWriter(0)
        reader.accept(decorate(KotlinMetadataRenamer(from, to, ClassRemapper(decorateOutput(writer), PackageRemapper(from, to)))), 0)
        return writer.toByteArray()
    }

    /**
     * Resolves Hibernate classes and members against a class loader that has Hibernate 7, remembering what is missing. Only a class that
     * the class loader reports as not found counts as missing: a class that exists but cannot be loaded there (an optional dependency of
     * Hibernate's that the node does not have), and a reflection failure while looking into an existing class, count as "exists", so that
     * the check never refuses more than it can prove. Members are looked up the way the JVM resolves them: in the class, its superclasses
     * and all their interfaces, and, for an interface, in `Object` too (an `Object` method called on an interface-typed receiver names the
     * interface as its owner).
     */
    private class HibernateResolver(private val classLoader: ClassLoader, private val bundledClass: (String) -> Boolean) {
        /** Descriptions of the missing classes and members, in the order found. */
        val missing = LinkedHashSet<String>()
        private val classes = HashMap<String, Class<*>?>()
        private val members = HashMap<String, Boolean>()

        fun checkType(type: Type) {
            when (type.sort) {
                Type.ARRAY -> checkType(type.elementType)
                Type.OBJECT -> checkClass(type.internalName)
                Type.METHOD -> {
                    type.argumentTypes.forEach(::checkType)
                    checkType(type.returnType)
                }
            }
        }

        fun checkMethod(owner: String, name: String, descriptor: String) {
            val clazz = classOf(owner) ?: return
            val exists = members.getOrPut("$owner.$name$descriptor") {
                runCatching {
                    if (name == "<init>") {
                        clazz.declaredConstructors.any { Type.getConstructorDescriptor(it) == descriptor }
                    } else {
                        hierarchy(clazz).any { type -> type.declaredMethods.any { it.name == name && Type.getMethodDescriptor(it) == descriptor } }
                    }
                }.getOrDefault(true)
            }
            if (!exists) {
                val arguments = Type.getArgumentTypes(descriptor).joinToString { it.className }
                missing += "method ${clazz.name}.${if (name == "<init>") "<init>" else name}($arguments)"
            }
        }

        fun checkField(owner: String, name: String) {
            val clazz = classOf(owner) ?: return
            val exists = members.getOrPut("$owner.$name") {
                runCatching { hierarchy(clazz).any { type -> type.declaredFields.any { it.name == name } } }.getOrDefault(true)
            }
            if (!exists) missing += "field ${clazz.name}.$name"
        }

        private fun checkClass(internalName: String) {
            if (internalName.startsWith(HIBERNATE)) classOf(internalName)
        }

        /**
         * The Hibernate class with the given internal name, or null if there is nothing to check: not a Hibernate class, bundled by the
         * CorDapp, missing (recorded once), or present but not loadable here.
         */
        private fun classOf(internalName: String): Class<*>? {
            if (!internalName.startsWith(HIBERNATE) || bundledClass(internalName)) return null
            return classes.getOrPut(internalName) {
                try {
                    Class.forName(internalName.replace('/', '.'), false, classLoader)
                } catch (e: ClassNotFoundException) {
                    missing += "class ${internalName.replace('/', '.')}"
                    null
                } catch (e: LinkageError) {
                    // The class is there but something it depends on is not; the CorDapp's use of it may still be fine.
                    null
                }
            }
        }

        /**
         * The class, its superclasses and every interface any of them implements, which is where the JVM resolves members; for an
         * interface, `Object` as well (JVMS 5.4.3.4).
         */
        private fun hierarchy(clazz: Class<*>): Sequence<Class<*>> = sequence {
            val seen = HashSet<Class<*>>()
            val queue = ArrayDeque<Class<*>>()
            queue += clazz
            if (clazz.isInterface) queue += Any::class.java
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (!seen.add(next)) continue
                yield(next)
                next.superclass?.let { queue += it }
                queue += next.interfaces
            }
        }
    }

    /** Resolves, through [resolver], every reference to a Hibernate class or member in the class it visits (the renamed class). */
    private class HibernateReferenceChecker(private val resolver: HibernateResolver, next: ClassVisitor) : ClassVisitor(Opcodes.ASM9, next) {
        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            superName?.let { resolver.checkType(Type.getObjectType(it)) }
            interfaces?.forEach { resolver.checkType(Type.getObjectType(it)) }
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            resolver.checkType(Type.getType(descriptor))
            return super.visitField(access, name, descriptor, signature, value)
        }

        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            resolver.checkType(Type.getMethodType(descriptor))
            exceptions?.forEach { resolver.checkType(Type.getObjectType(it)) }
            val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
            return object : MethodVisitor(Opcodes.ASM9, methodVisitor) {
                override fun visitTypeInsn(opcode: Int, type: String) {
                    resolver.checkType(Type.getObjectType(type))
                    super.visitTypeInsn(opcode, type)
                }

                override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                    resolver.checkField(owner, name)
                    resolver.checkType(Type.getType(descriptor))
                    super.visitFieldInsn(opcode, owner, name, descriptor)
                }

                override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                    checkHandle(owner, name, descriptor)
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }

                override fun visitInvokeDynamicInsn(name: String, descriptor: String, handle: Handle, vararg arguments: Any) {
                    arguments.forEach { if (it is Handle) checkHandle(it.owner, it.name, it.desc) }
                    super.visitInvokeDynamicInsn(name, descriptor, handle, *arguments)
                }

                override fun visitLdcInsn(value: Any) {
                    if (value is Type) resolver.checkType(value)
                    super.visitLdcInsn(value)
                }

                override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
                    resolver.checkType(Type.getType(descriptor))
                    super.visitMultiANewArrayInsn(descriptor, numDimensions)
                }

                override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
                    type?.let { resolver.checkType(Type.getObjectType(it)) }
                    super.visitTryCatchBlock(start, end, handler, type)
                }

                private fun checkHandle(owner: String, name: String, descriptor: String) {
                    if (owner.startsWith("[")) return
                    resolver.checkType(Type.getMethodType(descriptor))
                    resolver.checkMethod(owner, name, descriptor)
                }
            }
        }
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
