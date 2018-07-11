package net.corda

import net.corda.core.CordaRuntimeException
import net.corda.core.ClientRelevantError

class ClientRelevantException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause), ClientRelevantError