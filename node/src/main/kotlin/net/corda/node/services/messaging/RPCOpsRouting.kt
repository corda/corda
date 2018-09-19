package net.corda.node.services.messaging

import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.RPCOps
import net.corda.core.utilities.contextLogger

class RPCOpsRouting<out OPS : RPCOps>(private val rpcOpsMap: Map<CordaX500Name, OPS>) {

    companion object {
        private val logger = contextLogger()

        fun <OPS : RPCOps> singleton(name: CordaX500Name, rpcOps: OPS): RPCOpsRouting<OPS> {
            return RPCOpsRouting(mapOf(name to rpcOps))
        }
    }

    init {
        if(rpcOpsMap.isEmpty()) {
            throw IllegalStateException("RPC Ops mapping cannot be empty")
        }
    }

    fun names(): Set<CordaX500Name> = rpcOpsMap.keys

    operator fun get(targetLegalName: CordaX500Name): OPS {
        return rpcOpsMap[targetLegalName] ?: {
            val msg = "Cannot find RPC Ops for name: '$targetLegalName'"
            logger.error(msg)
            throw IllegalArgumentException(msg)
        }()
    }
}