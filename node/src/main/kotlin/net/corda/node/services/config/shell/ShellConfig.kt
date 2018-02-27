package net.corda.node.services.config.shell

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.config.SslOptions
import net.corda.nodeapi.internal.config.User

//re-packs data to Shell specific classes
fun NodeConfiguration.toShellConfig(): net.corda.shell.ShellConfiguration {

    val sslConfiguration = if (this.rpcOptions.useSsl) {
        SslOptions(this.rpcOptions.sslConfig.certificatesDirectory,
                this.rpcOptions.sslConfig.keyStorePassword, this.rpcOptions.sslConfig.trustStorePassword)
    } else {
        null
    }
    val localShellUser: User? = this.rpcUsers.firstOrNull { u -> u.username == "demo" }
    return net.corda.shell.ShellConfiguration(this.baseDirectory,
            localShellUser?.username ?: "", localShellUser?.password,
            this.rpcOptions.address ?: NetworkHostAndPort("localhost", 2222),
            sslConfiguration, this.sshd?.port, this.noLocalShell)
}