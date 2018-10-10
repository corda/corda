package net.corda.djvm.code

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import sandbox.net.corda.djvm.costing.RuntimeCostAccounter

/**
 * Helper functions for emitting code to a method body.
 *
 * @property methodVisitor The underlying visitor which controls all the byte code for the current method.
 */
@Suppress("unused")
class EmitterModule(
        private val methodVisitor: MethodVisitor
) {

    /**
     * Indicates whether the default instruction in the currently processed block is to be emitted or not.
     */
    var emitDefaultInstruction: Boolean = true
        private set

    /**
     * Indicates whether any custom code has been emitted in the applicable context.
     */
    var hasEmittedCustomCode: Boolean = false
        private set

    /**
     * Emit instruction for creating a new object of type [typeName].
     */
    fun new(typeName: String, opcode: Int = NEW) {
        hasEmittedCustomCode = true
        methodVisitor.visitTypeInsn(opcode, typeName)
    }

    /**
     * Emit instruction for creating a new object of type [T].
     */
    inline fun <reified T> new() {
        new(Type.getInternalName(T::class.java))
    }

    /**
     * Emit instruction for loading an integer constant onto the stack.
     */
    fun loadConstant(constant: Int) {
        hasEmittedCustomCode = true
        methodVisitor.visitLdcInsn(constant)
    }

    /**
     * Emit instruction for loading a string constant onto the stack.
     */
    fun loadConstant(constant: String) {
        hasEmittedCustomCode = true
        methodVisitor.visitLdcInsn(constant)
    }

    /**
     * Emit instruction for invoking a static method.
     */
    fun invokeStatic(owner: String, name: String, descriptor: String, isInterface: Boolean = false) {
        hasEmittedCustomCode = true
        methodVisitor.visitMethodInsn(INVOKESTATIC, owner, name, descriptor, isInterface)
    }

    /**
     * Emit instruction for invoking a special method, e.g. a constructor or a method on a super-type.
     */
    fun invokeSpecial(owner: String, name: String, descriptor: String, isInterface: Boolean = false) {
        hasEmittedCustomCode = true
        methodVisitor.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface)
    }

    /**
     * Emit instruction for invoking a special method on class [T], e.g. a constructor or a method on a super-type.
     */
    inline fun <reified T> invokeSpecial(name: String, descriptor: String, isInterface: Boolean = false) {
        invokeSpecial(Type.getInternalName(T::class.java), name, descriptor, isInterface)
    }

    /**
     * Emit instruction for popping one element off the stack.
     */
    fun pop() {
        hasEmittedCustomCode = true
        methodVisitor.visitInsn(POP)
    }

    /**
     * Emit instruction for duplicating the top of the stack.
     */
    fun duplicate() {
        hasEmittedCustomCode = true
        methodVisitor.visitInsn(DUP)
    }

    /**
     * Emit a sequence of instructions for instantiating and throwing an exception based on the provided message.
     */
    fun <T : Throwable> throwException(exceptionType: Class<T>, message: String) {
        hasEmittedCustomCode = true
        val exceptionName = Type.getInternalName(exceptionType)
        new(exceptionName)
        methodVisitor.visitInsn(DUP)
        methodVisitor.visitLdcInsn(message)
        invokeSpecial(exceptionName, "<init>", "(Ljava/lang/String;)V")
        methodVisitor.visitInsn(ATHROW)
    }

    inline fun <reified T : Throwable> throwException(message: String) = throwException(T::class.java, message)

    /**
     * Emit instruction for returning from "void" method.
     */
    fun returnVoid() {
        methodVisitor.visitInsn(RETURN)
        hasEmittedCustomCode = true
    }

    /**
     * Emit instructions for a new line number.
     */
    fun lineNumber(line: Int) {
        val label = Label()
        methodVisitor.visitLabel(label)
        methodVisitor.visitLineNumber(line, label)
        hasEmittedCustomCode = true
    }

    /**
     * Tell the code writer not to emit the default instruction.
     */
    fun preventDefault() {
        emitDefaultInstruction = false
    }

    /**
     * Emit instruction for invoking a method on the static runtime cost accounting and instrumentation object.
     */
    fun invokeInstrumenter(methodName: String, methodSignature: String) {
        invokeStatic(RuntimeCostAccounter.TYPE_NAME, methodName, methodSignature)
    }

}