package net.corda.djvm.rules.implementation

import net.corda.djvm.code.EMIT_HANDLING_EXCEPTIONS
import net.corda.djvm.code.Emitter
import net.corda.djvm.code.EmitterContext
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.CodeLabel
import net.corda.djvm.code.instructions.TryBlock
import org.objectweb.asm.Label

/**
 * Converts an exception from [java.lang.Throwable] to [sandbox.java.lang.Throwable]
 * at the beginning of either a catch block or a finally block.
 */
class HandleExceptionUnwrapper : Emitter {
    private val handlers = mutableMapOf<Label, String>()

    override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
        if (instruction is TryBlock) {
            handlers[instruction.handler] = instruction.typeName
        } else if (instruction is CodeLabel) {
            handlers[instruction.label]?.let { exceptionType ->
                if (exceptionType.isNotEmpty()) {
                    /**
                     * This is a catch block; the wrapping function is allowed to throw exceptions.
                     */
                    invokeStatic("sandbox/java/lang/DJVM", "catch", "(Ljava/lang/Throwable;)Lsandbox/java/lang/Throwable;")

                    /**
                     * When catching exceptions, we also need to tell the verifier which
                     * which kind of [sandbox.java.lang.Throwable] to expect this to be.
                     */
                    castObjectTo(exceptionType)
                } else {
                    /**
                     * This is a finally block; the wrapping function MUST NOT throw exceptions.
                     */
                    invokeStatic("sandbox/java/lang/DJVM", "finally", "(Ljava/lang/Throwable;)Lsandbox/java/lang/Throwable;")
                }
            }
        }
    }

    override val priority: Int
        get() = EMIT_HANDLING_EXCEPTIONS
}