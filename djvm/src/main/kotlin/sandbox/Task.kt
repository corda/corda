package sandbox

import sandbox.java.lang.sandbox
import sandbox.java.lang.unsandbox

typealias JFunction<TInput, TOutput> = sandbox.java.util.function.Function<TInput, TOutput>

@Suppress("unused")
class Task(private val function: JFunction<Any?, Any?>?) : JFunction<Any?, Any?> {

    override fun apply(input: Any?): Any? {
        return function?.apply(input?.sandbox())?.unsandbox()
    }

}
