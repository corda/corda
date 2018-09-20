package net.corda.tools.error.codes.server.commons.logging

interface LoggingContext {

    val trace: Trace

    val externalTrace: Trace?

    val description: String
}

