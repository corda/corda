package net.corda.djvm.rewiring

import net.corda.djvm.analysis.ExceptionResolver.Companion.isDJVMException
import net.corda.djvm.code.djvmException
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*

/**
 * Generates a synthetic [Throwable] class that will wrap a [sandbox.java.lang.Throwable].
 * Only exceptions which are NOT thrown by the JVM will be accompanied one of these.
 */
class ThrowableWrapperFactory(
    private val className: String,
    private val superName: String
) {
    companion object {
        const val CONSTRUCTOR_DESCRIPTOR = "(Lsandbox/java/lang/Throwable;)V"
        const val FIELD_TYPE = "Lsandbox/java/lang/Throwable;"
        const val THROWABLE_FIELD = "t"

        fun toByteCode(className: String, superName: String): ByteCode {
            val bytecode: ByteArray = with(ClassWriter(0)) {
                ThrowableWrapperFactory(className, superName).accept(this)
                toByteArray()
            }
            return ByteCode(bytecode, true)
        }
    }

    /**
     * Write bytecode for synthetic throwable wrapper class. All of
     * these classes implement [sandbox.java.lang.DJVMException],
     * either directly or indirectly.
     */
    fun accept(writer: ClassWriter) = with(writer) {
        if (isDJVMException(superName)) {
            childClass()
        } else {
            baseClass()
        }
    }

    /**
     * This is a "base" wrapper class that inherits from a JVM exception.
     *
     * <code>
     *     public class CLASSNAME extends JAVA_EXCEPTION implements DJVMException {
     *         private final sandbox.java.lang.Throwable t;
     *
     *         public CLASSNAME(sandbox.java.lang.Throwable t) {
     *             this.t = t;
     *         }
     *
     *         @Override
     *         public final sandbox.java.lang.Throwable getThrowable() {
     *             return t;
     *         }
     *     }
     * </code>
     */
    private fun ClassWriter.baseClass() {
        // Class definition
        visit(
            V1_8,
            ACC_SYNTHETIC or ACC_PUBLIC,
            className,
            null,
            superName,
            arrayOf(djvmException)
        )

        // Private final field to hold the sandbox throwable object.
        visitField(ACC_PRIVATE or ACC_FINAL, THROWABLE_FIELD, FIELD_TYPE, null, null)

        // Constructor
        visitMethod(ACC_PUBLIC, "<init>", CONSTRUCTOR_DESCRIPTOR, null, null).also { mv ->
            mv.visitCode()
            mv.visitVarInsn(ALOAD, 0)
            mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false)
            mv.visitVarInsn(ALOAD, 0)
            mv.visitVarInsn(ALOAD, 1)
            mv.visitFieldInsn(PUTFIELD, className, THROWABLE_FIELD, FIELD_TYPE)
            mv.visitInsn(RETURN)
            mv.visitMaxs(2, 2)
            mv.visitEnd()
        }

        // Getter method for the sandbox throwable object.
        visitMethod(ACC_PUBLIC or ACC_FINAL, "getThrowable", "()$FIELD_TYPE", null, null).also { mv ->
            mv.visitCode()
            mv.visitVarInsn(ALOAD, 0)
            mv.visitFieldInsn(GETFIELD, className, THROWABLE_FIELD, FIELD_TYPE)
            mv.visitInsn(ARETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        // End of class
        visitEnd()
    }

    /**
     * This wrapper class inherits from another wrapper class.
     *
     * <code>
     *     public class CLASSNAME extends SUPERNAME {
     *         public CLASSNAME(sandbox.java.lang.Throwable t) {
     *             super(t);
     *         }
     *     }
     * </code>
     */
    private fun ClassWriter.childClass() {
        // Class definition
        visit(
            V1_8,
            ACC_SYNTHETIC or ACC_PUBLIC,
            className,
            null,
            superName,
            arrayOf()
        )

        // Constructor
        visitMethod(ACC_PUBLIC, "<init>", CONSTRUCTOR_DESCRIPTOR, null, null).also { mv ->
            mv.visitCode()
            mv.visitVarInsn(ALOAD, 0)
            mv.visitVarInsn(ALOAD, 1)
            mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", CONSTRUCTOR_DESCRIPTOR, false)
            mv.visitInsn(RETURN)
            mv.visitMaxs(2, 2)
            mv.visitEnd()
        }

        // End of class
        visitEnd()
    }
}