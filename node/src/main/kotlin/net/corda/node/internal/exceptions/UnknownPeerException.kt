package net.corda.node.internal.exceptions

import net.corda.core.CordaRuntimeException

class UnknownPeerException(message: String, cause: Throwable?) : CordaRuntimeException(message, cause) {
    constructor(msg: String) : this(msg, null)
}
