package net.corda

import net.corda.core.CordaRuntimeException
import net.corda.nodeapi.exceptions.RpcSerializableError

class ClientRelevantException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause), RpcSerializableError