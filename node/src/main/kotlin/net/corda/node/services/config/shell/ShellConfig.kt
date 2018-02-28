package net.corda.node.services.config.shell

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.shell.ShellConfiguration
import net.corda.shell.ShellSslOptions

private const val SSH_PORT = 2222

//re-packs data to Shell specific classes
fun NodeConfiguration.toShellConfig(): ShellConfiguration {

    val sslConfiguration = if (this.rpcOptions.useSsl) {
        ShellSslOptions(this.rpcOptions.sslConfig.certificatesDirectory,
                this.rpcOptions.sslConfig.keyStorePassword, this.rpcOptions.sslConfig.trustStorePassword)
    } else {
        null
    }
    val localShellUser: User? = this.rpcUsers.firstOrNull { u -> u.username == "demo" } // TODO Simon investigate creating shell user at startup if embedded shell is started (it's obviously in dev mode only)
    return ShellConfiguration(this.baseDirectory,
            localShellUser?.username ?: "", localShellUser?.password,
            this.rpcOptions.address ?: NetworkHostAndPort("localhost", SSH_PORT),
            sslConfiguration, this.sshd?.port, this.noLocalShell)
}