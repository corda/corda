package net.corda.tools.error.codes.server.utils

import net.corda.tools.error.codes.server.commons.Trace

interface LoggingContext {

    val trace: Trace

    val externalTrace: Trace?

    val description: String
}

