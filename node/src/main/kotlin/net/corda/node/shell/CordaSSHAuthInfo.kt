package net.corda.node.shell

import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.node.utilities.ANSIProgressRenderer
import org.crsh.auth.AuthInfo

class CordaSSHAuthInfo(val successful: Boolean, val rpcOps: InternalCordaRPCOps, val ansiProgressRenderer: ANSIProgressRenderer? = null) : AuthInfo {
    override fun isSuccessful(): Boolean = successful
}