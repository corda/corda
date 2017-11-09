package net.corda.node.shell

import net.corda.core.messaging.CordaRPCOps
import org.crsh.auth.AuthInfo

class CordaSSHAuthInfo(val successful: Boolean, val rpcOps: CordaRPCOps) : AuthInfo {
    override fun isSuccessful(): Boolean = successful
}