package net.corda.gradle.jarfilter

import org.gradle.api.InvalidUserDataException
import org.gradle.api.logging.Logger
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*

class ClassTransformer private constructor (
    visitor: ClassVisitor,
    logger: Logger,
    kotlinMetadata: MutableMap<String, List<String>>,
    private val removeAnnotations: Set<String>,
    private val deleteAnnotations: Set<String>,
    private val stubAnnotations: Set<String>,
    private val unwantedClasses: MutableSet<String>,
    private val unwantedFields: MutableSet<FieldElement>,
    private val deletedMethods: MutableSet<MethodElement>,
    private val stubbedMethods: MutableSet<MethodElement>
) : KotlinAwareVisitor(ASM6, visitor, logger, kotlinMetadata), Repeatable<ClassTransformer> {
    constructor(
        visitor: ClassVisitor,
        logger: Logger,
        removeAnnotations: Set<String>,
        deleteAnnotations: Set<String>,
        stubAnnotations: Set<String>,
        unwantedClasses: MutableSet<String>
    ) : this(
        visitor = visitor,
        logger = logger,
        kotlinMetadata = mutableMapOf(),
        removeAnnotations = removeAnnotations,
        deleteAnnotations = deleteAnnotations,
        stubAnnotations = stubAnnotations,
        unwantedClasses = unwantedClasses,
        unwantedFields = mutableSetOf(),
        deletedMethods = mutableSetOf(),
        stubbedMethods = mutableSetOf()
    )

    private var _className: String = "(unknown)"
    val className: String get() = _className

    val isUnwantedClass: Boolean get() = isUnwantedClass(className)
    override val hasUnwantedElements: Boolean
        get() = unwantedFields.isNotEmpty()
                  || deletedMethods.isNotEmpty()
                  || stubbedMethods.isNotEmpty()
                  || super.hasUnwantedElements

    private fun isUnwantedClass(name: String): Boolean = unwantedClasses.contains(name)
    private fun hasDeletedSyntheticMethod(name: String): Boolean = deletedMethods.any { method ->
        name.startsWith("$className\$${method.visibleName}\$")
    }

    override fun recreate(visitor: ClassVisitor) = ClassTransformer(
        visitor = visitor,
        logger = logger,
        kotlinMetadata = kotlinMetadata,
        removeAnnotations = removeAnnotations,
        deleteAnnotations = deleteAnnotations,
        stubAnnotations = stubAnnotations,
        unwantedClasses = unwantedClasses,
        unwantedFields = unwantedFields,
        deletedMethods = deletedMethods,
        stubbedMethods = stubbedMethods
    )

    override fun visit(version: Int, access: Int, clsName: String, signature: String?, superName: String?, interfaces: Array<String>?) {
        _className = clsName
        logger.info("Class {}", clsName)
        super.visit(version, access, clsName, signature, superName, interfaces)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        if (removeAnnotations.contains(descriptor)) {
            logger.info("- Removing annotation {}", descriptor)
            return null
        } else if (deleteAnnotations.contains(descriptor)) {
            if (unwantedClasses.add(className)) {
                logger.info("- Identified class {} as unwanted", className)
            }
        }
        return super.visitAnnotation(descriptor, visible)
    }

    override fun visitField(access: Int, fieldName: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
        val field = FieldElement(fieldName, descriptor)
        logger.debug("--- field ---> {}", field)
        if (unwantedFields.contains(field)) {
            logger.info("- Deleted field {},{}", field.name, field.descriptor)
            unwantedFields.remove(field)
            return null
        }
        val fv = super.visitField(access, fieldName, descriptor, signature, value) ?: return null
        return UnwantedFieldAdapter(fv, field)
    }

    override fun visitMethod(access: Int, methodName: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
        val method = MethodElement(methodName, descriptor, access)
        logger.debug("--- method ---> {}", method)
        if (deletedMethods.contains(method)) {
            logger.info("- Deleted method {}{}", method.name, method.descriptor)
            deletedMethods.remove(method)
            return null
        }

        /*
         * Write the byte-code for the method's prototype, then check whether
         * we need to replace the method's body with our "stub" code.
         */
        val mv = super.visitMethod(access, methodName, descriptor, signature, exceptions) ?: return null
        if (stubbedMethods.contains(method)) {
            logger.info("- Stubbed out method {}{}", method.name, method.descriptor)
            stubbedMethods.remove(method)
            return if (method.isVoidFunction) VoidStubMethodAdapter(mv) else ThrowingStubMethodAdapter(mv)
        }

        return UnwantedMethodAdapter(mv, method)
    }

    override fun visitInnerClass(clsName: String, outerName: String?, innerName: String?, access: Int) {
        logger.debug("--- inner class {} [outer: {}, inner: {}]", clsName, outerName, innerName)
        if (isUnwantedClass || hasDeletedSyntheticMethod(clsName)) {
            if (unwantedClasses.add(clsName)) {
                logger.info("- Deleted inner class {}", clsName)
            }
        } else if (isUnwantedClass(clsName)) {
            logger.info("- Deleted reference to inner class: {}", clsName)
        } else {
            super.visitInnerClass(clsName, outerName, innerName, access)
        }
    }

    override fun visitOuterClass(outerName: String, methodName: String?, methodDescriptor: String?) {
        logger.debug("--- outer class {} [enclosing method {},{}]", outerName, methodName, methodDescriptor)
        if (isUnwantedClass(outerName)) {
            logger.info("- Deleted reference to outer class {}", outerName)
        } else {
            super.visitOuterClass(outerName, methodName, methodDescriptor)
        }
    }

    override fun visitEnd() {
        if (isUnwantedClass) {
            /*
             * Optimisation: Don't rewrite the Kotlin @Metadata
             * annotation if we're going to delete this class.
             */
            kotlinMetadata.clear()
        }
        super.visitEnd()
        /*
         * Some elements were created based on unreliable information,
         * such as Kotlin @Metadata annotations. We cannot rely on
         * these actually existing in the bytecode, and so we expire
         * them after a fixed number of passes.
         */
        deletedMethods.removeIf(MethodElement::isExpired)
        unwantedFields.removeIf(FieldElement::isExpired)
    }

    /**
     * Removes the deleted methods and fields from the Kotlin Class metadata.
     */
    override fun transformClassMetadata(d1: List<String>, d2: List<String>): List<String> {
        val partitioned = deletedMethods.groupBy(MethodElement::isConstructor)
        val prefix = "$className$"
        return ClassMetadataTransformer(
                logger = logger,
                deletedFields = unwantedFields,
                deletedFunctions = partitioned[false] ?: emptyList(),
                deletedConstructors = partitioned[true] ?: emptyList(),
                deletedNestedClasses = unwantedClasses.filter { it.startsWith(prefix) }.map { it.drop(prefix.length) },
                deletedClasses = unwantedClasses,
                handleExtraMethod = ::delete,
                d1 = d1,
                d2 = d2)
            .transform()
    }

    /**
     * Removes the deleted methods and fields from the Kotlin Package metadata.
     */
    override fun transformPackageMetadata(d1: List<String>, d2: List<String>): List<String> {
        return PackageMetadataTransformer(
                logger = logger,
                deletedFields = unwantedFields,
                deletedFunctions = deletedMethods,
                handleExtraMethod = ::delete,
                d1 = d1,
                d2 = d2)
            .transform()
    }

    /**
     * Callback function to mark extra methods for deletion.
     * This will override a request for stubbing.
     */
    private fun delete(method: MethodElement) {
        if (deletedMethods.add(method) && stubbedMethods.remove(method)) {
            logger.warn("-- method {}{} will be deleted instead of stubbed out",
                         method.name, method.descriptor)
        }
    }

    /**
     * Analyses the field to decide whether it should be deleted.
     */
    private inner class UnwantedFieldAdapter(fv: FieldVisitor, private val field: FieldElement) : FieldVisitor(api, fv) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (removeAnnotations.contains(descriptor)) {
                logger.info("- Removing annotation {} from field {},{}", descriptor, field.name, field.descriptor)
                return null
            } else if (deleteAnnotations.contains(descriptor)) {
                if (unwantedFields.add(field)) {
                    logger.info("- Identified field {},{} as unwanted", field.name, field.descriptor)
                }
            }
            return super.visitAnnotation(descriptor, visible)
        }
    }

    /**
     * Analyses the method to decide whether it should be deleted.
     */
    private inner class UnwantedMethodAdapter(mv: MethodVisitor, private val method: MethodElement) : MethodVisitor(api, mv) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (removeAnnotations.contains(descriptor)) {
                logger.info("- Removing annotation {} from method {}{}", descriptor, method.name, method.descriptor)
                return null
            } else if (deleteAnnotations.contains(descriptor)) {
                if (deletedMethods.add(method)) {
                    logger.info("- Identified method {}{} for deletion", method.name, method.descriptor)
                }
                if (method.isKotlinSynthetic("annotations")) {
                    val extensionType = method.descriptor.extensionType
                    if (unwantedFields.add(FieldElement(name = method.visibleName, extension = extensionType))) {
                        logger.info("-- also identified property or typealias {},{} for deletion", method.visibleName, extensionType)
                    }
                }
            } else if (stubAnnotations.contains(descriptor) && (method.access and (ACC_ABSTRACT or ACC_SYNTHETIC)) == 0) {
                if (stubbedMethods.add(method)) {
                    logger.info("- Identified method {}{} for stubbing out", method.name, method.descriptor)
                }
            }
            return super.visitAnnotation(descriptor, visible)
        }

        override fun visitMethodInsn(opcode: Int, ownerName: String, methodName: String, descriptor: String, isInterface: Boolean) {
            if ((isUnwantedClass(ownerName) || (ownerName == className && deletedMethods.contains(MethodElement(methodName, descriptor))))
                    && !stubbedMethods.contains(method)) {
                if (deletedMethods.add(method)) {
                    logger.info("- Unwanted invocation of method {},{}{} from method {}{}", ownerName, methodName, descriptor, method.name, method.descriptor)
                }
            }
            super.visitMethodInsn(opcode, ownerName, methodName, descriptor, isInterface)
         }

        override fun visitFieldInsn(opcode: Int, ownerName: String, fieldName: String, descriptor: String) {
            if ((isUnwantedClass(ownerName) || (ownerName == className && unwantedFields.contains(FieldElement(fieldName, descriptor))))
                    && !stubbedMethods.contains(method)) {
                if (method.isConstructor) {
                    when (opcode) {
                        GETFIELD, GETSTATIC -> {
                            when (descriptor) {
                                "I", "S", "B", "C", "Z" -> visitIntInsn(BIPUSH, 0)
                                "J" -> visitInsn(LCONST_0)
                                "F" -> visitInsn(FCONST_0)
                                "D" -> visitInsn(DCONST_0)
                                else -> visitInsn(ACONST_NULL)
                            }
                        }
                        PUTFIELD, PUTSTATIC -> {
                            when (descriptor) {
                                "J", "D" -> visitInsn(POP2)
                                else -> visitInsn(POP)
                            }
                        }
                        else -> throw InvalidUserDataException("Unexpected opcode $opcode")
                    }
                    logger.info("- Unwanted reference to field {},{},{} REMOVED from constructor {}{}",
                                  ownerName, fieldName, descriptor, method.name, method.descriptor)
                    return
                } else if (deletedMethods.add(method)) {
                    logger.info("- Unwanted reference to field {},{},{} from method {}{}",
                                  ownerName, fieldName, descriptor, method.name, method.descriptor)
                }
            }
            super.visitFieldInsn(opcode, ownerName, fieldName, descriptor)
        }
    }

    /**
     * Write "stub" byte-code for this method, preserving its other annotations.
     * The method's original byte-code is discarded.
     */
    private abstract inner class StubbingMethodAdapter(mv: MethodVisitor) : MethodVisitor(api, mv) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            return if (stubAnnotations.contains(descriptor)) null else mv.visitAnnotation(descriptor, visible)
        }

        protected abstract fun writeStubCode()

        final override fun visitCode() {
            with (mv) {
                visitCode()
                writeStubCode()
                visitMaxs(-1, -1)  // Trigger computation of the max values.
                visitEnd()
            }

            // Prevent this visitor from writing any more byte-code.
            mv = null
        }
    }

    /**
     * Write a method that throws [UnsupportedOperationException] with message "Method has been deleted".
     */
    private inner class ThrowingStubMethodAdapter(mv: MethodVisitor) : StubbingMethodAdapter(mv) {
        override fun writeStubCode() {
            with (mv) {
                val throwEx = Label()
                visitLabel(throwEx)
                visitLineNumber(0, throwEx)
                visitTypeInsn(NEW, "java/lang/UnsupportedOperationException")
                visitInsn(DUP)
                visitLdcInsn("Method has been deleted")
                visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false)
                visitInsn(ATHROW)
            }
        }
    }

    /**
     * Write an empty method. Can only be applied to methods that return void.
     */
    private inner class VoidStubMethodAdapter(mv: MethodVisitor) : StubbingMethodAdapter(mv) {
        override fun writeStubCode() {
            mv.visitInsn(RETURN)
        }
    }
}
