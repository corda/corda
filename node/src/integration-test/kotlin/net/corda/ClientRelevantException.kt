package net.corda

import net.corda.core.CordaRuntimeException
import net.corda.core.flows.ClientRelevantError

class ClientRelevantException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause), ClientRelevantError