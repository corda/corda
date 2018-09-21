package net.corda.tools.error.codes.server.web.endpoints.template

import net.corda.tools.error.codes.server.context.InvocationContext

internal class RequestValidationException(val errors: Set<String>, val invocationContext: InvocationContext) : Exception() {

    constructor(error: String, invocationContext: InvocationContext) : this(setOf(error), invocationContext)

    init {
        require(errors.isNotEmpty())
    }
}