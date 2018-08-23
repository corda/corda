package net.corda.djvm.code

/**
 * An emitter is a hook for [ClassMutator], from where one can modify the byte code of a class method.
 */
interface Emitter {

    /**
     * Hook for providing modifications to an instruction in a method body. One can also prepend and append instructions
     * by using the [EmitterContext], and skip the default instruction altogether by invoking
     * [EmitterModule.preventDefault] from within [EmitterContext.emit].
     *
     * @param context The context from which the emitter is invoked. By calling [EmitterContext.emit], one gets access
     * to an instance of [EmitterModule] from within the supplied closure. From there, one can emit new instructions and
     * intercept the original instruction (for instance, modify or delete the instruction).
     * @param instruction The instruction currently being processed.
     */
    fun emit(context: EmitterContext, instruction: Instruction)

    /**
     * Indication of whether or not the emitter performs instrumentation for tracing inside the sandbox.
     */
    val isTracer: Boolean
        get() = false

}