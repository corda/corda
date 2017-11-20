package net.corda.node.shell

import net.corda.core.messaging.CordaRPCOps
import net.corda.node.utilities.ANSIProgressRenderer
import org.crsh.auth.AuthInfo

class CordaSSHAuthInfo(val successful: Boolean, val rpcOps: CordaRPCOps, val ansiProgressRenderer: ANSIProgressRenderer? = null) : AuthInfo {
    override fun isSuccessful(): Boolean = successful
}