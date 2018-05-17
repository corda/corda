package net.corda

import net.corda.core.CordaRuntimeException
import net.corda.core.flows.RpcSerializableError

class ClientRelevantException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause), RpcSerializableError