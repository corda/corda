package net.corda.gradle.jarfilter

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.Flags.*
import org.jetbrains.kotlin.metadata.deserialization.TypeTable
import org.jetbrains.kotlin.metadata.jvm.JvmProtoBuf.*
import org.jetbrains.kotlin.metadata.jvm.deserialization.BitEncoding
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmNameResolver
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil.EXTENSION_REGISTRY
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil.getJvmConstructorSignature
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import java.io.ByteArrayInputStream

/**
 * This is (hopefully?!) a temporary solution for classes with [JvmOverloads] constructors.
 * We need to be able to annotate ONLY the secondary constructors for such classes, but Kotlin
 * will apply any annotation to all constructors equally. Nor can we replace the overloaded
 * constructor with individual constructors because this will break ABI compatibility. (Kotlin
 * generates a synthetic public constructor to handle default parameter values.)
 *
 * This transformer identifies a class's primary constructor and removes all of its unwanted annotations.
 * It will become superfluous when Kotlin allows us to target only the secondary constructors with our
 * filtering annotations in the first place.
 */
class SanitisingTransformer(visitor: ClassVisitor, logger: Logger, private val unwantedAnnotations: Set<String>)
    : KotlinBeforeProcessor(ASM6, visitor, logger, mutableMapOf()) {

    var isModified: Boolean = false
        private set
    override val level: LogLevel = LogLevel.DEBUG

    private var className: String = "(unknown)"
    private var primaryConstructor: MethodElement? = null

    override fun processPackageMetadata(d1: List<String>, d2: List<String>): List<String> = emptyList()

    override fun processClassMetadata(d1: List<String>, d2: List<String>): List<String> {
        val input = ByteArrayInputStream(BitEncoding.decodeBytes(d1.toTypedArray()))
        val stringTableTypes = StringTableTypes.parseDelimitedFrom(input, EXTENSION_REGISTRY)
        val nameResolver = JvmNameResolver(stringTableTypes, d2.toTypedArray())
        val message = ProtoBuf.Class.parseFrom(input, EXTENSION_REGISTRY)
        val typeTable = TypeTable(message.typeTable)

        for (constructor in message.constructorList) {
            if (!IS_SECONDARY.get(constructor.flags)) {
                val signature = getJvmConstructorSignature(constructor, nameResolver, typeTable) ?: break
                primaryConstructor = MethodElement("<init>", signature.drop("<init>".length))
                logger.log(level, "Class {} has primary constructor {}", className, signature)
                break
            }
        }
        return emptyList()
    }

    override fun visit(version: Int, access: Int, clsName: String, signature: String?, superName: String?, interfaces: Array<String>?) {
        className = clsName
        super.visit(version, access, clsName, signature, superName, interfaces)
    }

    override fun visitMethod(access: Int, methodName: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
        val method = MethodElement(methodName, descriptor, access)
        val mv = super.visitMethod(access, methodName, descriptor, signature, exceptions) ?: return null
        return if (method == primaryConstructor) SanitisingMethodAdapter(mv, method) else mv
    }

    private inner class SanitisingMethodAdapter(mv: MethodVisitor, private val method: MethodElement) : MethodVisitor(api, mv) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (unwantedAnnotations.contains(descriptor)) {
                logger.info("Sanitising annotation {} from method {}.{}{}", descriptor, className, method.name, method.descriptor)
                isModified = true
                return null
            }
            return super.visitAnnotation(descriptor, visible)
        }
    }
}
