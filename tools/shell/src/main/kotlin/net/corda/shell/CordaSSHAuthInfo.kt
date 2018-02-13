package net.corda.shell

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.messaging.CordaRPCOps
import net.corda.shell.InteractiveShell.createOutputMapper
import net.corda.shell.utlities.ANSIProgressRenderer
import org.crsh.auth.AuthInfo

class CordaSSHAuthInfo(val successful: Boolean, val rpcOps: CordaRPCOps, val ansiProgressRenderer: ANSIProgressRenderer? = null) : AuthInfo {
    override fun isSuccessful(): Boolean = successful

    val yamlInputMapper: ObjectMapper by lazy {
        createOutputMapper(rpcOps)
    }
}