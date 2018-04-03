package net.corda.node.services.config.shell

import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.Permissions
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.shouldInitCrashShell
import net.corda.nodeapi.internal.config.User
import net.corda.tools.shell.ShellConfiguration
import net.corda.tools.shell.ShellConfiguration.Companion.COMMANDS_DIR
import net.corda.tools.shell.ShellConfiguration.Companion.CORDAPPS_DIR
import net.corda.tools.shell.ShellConfiguration.Companion.SSHD_HOSTKEY_DIR
import net.corda.tools.shell.ShellConfiguration.Companion.SSH_PORT
import net.corda.tools.shell.ShellSslOptions


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
            commandsDirectory = this.baseDirectory / COMMANDS_DIR,
            cordappsDirectory = this.baseDirectory.toString() / CORDAPPS_DIR,
            user = localShellUser.username,
            password = localShellUser.password,
            hostAndPort = this.rpcOptions.address ?: NetworkHostAndPort("localhost", SSH_PORT),
            ssl = sslConfiguration,
            sshdPort = this.sshd?.port,
            sshHostKeyDirectory = this.baseDirectory / SSHD_HOSTKEY_DIR,
            noLocalShell = this.noLocalShell)
}

fun localShellUser() = User("shell", "shell", setOf(Permissions.all()))
