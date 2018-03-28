package net.corda

import net.corda.core.CordaRuntimeException
import net.corda.nodeapi.exceptions.WithClientRelevantMessage

class ClientRelevantException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause), WithClientRelevantMessage