package net.corda.node.services.config.shell

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.Permissions
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.shouldInitCrashShell
import net.corda.nodeapi.internal.config.User
import net.corda.shell.ShellConfiguration
import net.corda.shell.ShellSslOptions
import java.nio.file.Paths

private const val SSH_PORT = 2222

//re-packs data to Shell specific classes
fun NodeConfiguration.toShellConfig(): ShellConfiguration {

    val sslConfiguration = if (this.rpcOptions.useSsl) {
        with(this.rpcOptions.sslConfig) {
            ShellSslOptions(sslKeystore,
                    keyStorePassword,
                    trustStoreFile,
                    trustStorePassword)
        }
    } else {
        null
    }
    val localShellUser: User = localShellUser()
    return ShellConfiguration(
            shellDirectory = this.baseDirectory,
            cordappsDirectory = Paths.get(this.baseDirectory.toString() + "/cordapps"),
            user = localShellUser.username,
            password = localShellUser.password,
            hostAndPort = this.rpcOptions.address ?: NetworkHostAndPort("localhost", SSH_PORT),
            ssl = sslConfiguration,
            sshdPort = this.sshd?.port,
            noLocalShell = this.noLocalShell)
}

private fun localShellUser() = User("shell", "shell", setOf(Permissions.all()))
fun NodeConfiguration.shellUser() = shouldInitCrashShell()?.let { localShellUser() }
