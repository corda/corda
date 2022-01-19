package net.corda.tools.shell

import org.crsh.auth.AuthInfo
import org.crsh.auth.DisconnectPlugin
import org.crsh.plugin.CRaSHPlugin

class CordaDisconnectPlugin : CRaSHPlugin<DisconnectPlugin>(), DisconnectPlugin {
    override fun getImplementation() = this

    override fun onDisconnect(userName: String?, authInfo: AuthInfo?) {
        (authInfo as? CordaSSHAuthInfo)?.cleanUp()
    }
}
