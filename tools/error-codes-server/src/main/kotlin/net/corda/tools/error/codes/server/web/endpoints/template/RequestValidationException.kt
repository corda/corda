package net.corda.tools.error.codes.server.web.endpoints.template

import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.WithInvocationContext

internal class RequestValidationException private constructor(message: String, val errors: Set<String>, override val invocationContext: InvocationContext) : Exception(message), WithInvocationContext {

    companion object {

        private fun format(message: String, errors: Set<String>): String {

            require(errors.isNotEmpty())
            return "$message (errors: ${errors.joinToString(", ", "[", "]")})"
        }

        @JvmStatic
        fun withError(error: String, invocationContext: InvocationContext): RequestValidationException = RequestValidationException(error, setOf(error), invocationContext)

        @JvmStatic
        fun withErrors(message: String, errors: Set<String>, invocationContext: InvocationContext): RequestValidationException = RequestValidationException(format(message, errors), errors, invocationContext)
    }
}