package net.corda.tools.error.codes.server.web.endpoints.template

internal class RequestValidationException private constructor(message: String, val errors: Set<String>) : Exception(message) {

    companion object {

        private fun format(message: String, errors: Set<String>): String {

            require(errors.isNotEmpty())
            return "$message (errors: ${errors.joinToString(", ", "[", "]")})"
        }

        @JvmStatic
        fun withError(error: String): RequestValidationException = RequestValidationException(error, setOf(error))

        @JvmStatic
        fun withErrors(message: String, errors: Set<String>): RequestValidationException = RequestValidationException(format(message, errors), errors)
    }
}