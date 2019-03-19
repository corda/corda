@file:JvmName("TaskTypes")
package sandbox

import sandbox.java.lang.sandbox
import sandbox.java.lang.unsandbox

typealias SandboxFunction<TInput, TOutput> = sandbox.java.util.function.Function<TInput, TOutput>

internal fun isEntryPoint(elt: java.lang.StackTraceElement): Boolean {
    return elt.className == "sandbox.Task" && elt.methodName == "apply"
}

class Task(private val function: SandboxFunction<in Any?, out Any?>?) : SandboxFunction<Any?, Any?> {

    /**
     * This function runs inside the sandbox. It marshalls the input
     * object to its sandboxed equivalent, executes the user's code
     * and then marshalls the result out again.
     *
     * The marshalling should be effective for Java primitives,
     * Strings and Enums, as well as for arrays of these types.
     */
    override fun apply(input: Any?): Any? {
        return function?.apply(input?.sandbox())?.unsandbox()
    }

}
