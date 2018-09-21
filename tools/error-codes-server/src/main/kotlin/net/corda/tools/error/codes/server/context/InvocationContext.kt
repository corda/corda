package net.corda.tools.error.codes.server.context

import net.corda.tools.error.codes.server.commons.logging.Logger
import net.corda.tools.error.codes.server.commons.logging.LoggingContext
import net.corda.tools.error.codes.server.commons.logging.Trace

interface InvocationContext : LoggingContext {

    companion object {

        fun newInstance(trace: Trace = Trace.newInstance(), externalTrace: Trace? = null): InvocationContext = InvocationContextImpl(trace, externalTrace)
    }
}

interface WithInvocationContext {

    val invocationContext: InvocationContext
}

private data class InvocationContextImpl(override val trace: Trace, override val externalTrace: Trace?) : InvocationContext {

    override val description: String

    init {
        description = mapOf(*trace.description(), *externalTrace?.description("external") ?: emptyArray()).toString()
    }

    private fun Trace.description(prefix: String = ""): Array<Pair<String, Any>> {

        return arrayOf("${prefix}invocation_id" to invocationId.value, "${prefix}invocation_timestamp" to invocationId.timestamp, "${prefix}session_id" to sessionId.timestamp, "${prefix}session_timestamp" to sessionId.timestamp)
    }
}

inline fun <reified T : Any> loggerFor(): Logger<InvocationContext> = Logger.forType(T::class)