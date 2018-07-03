package net.corda.tools.shell

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.messaging.ClientRpcSslOptions
import net.corda.nodeapi.internal.config.SSLConfiguration
import java.nio.file.Path

data class ShellConfiguration(
        val commandsDirectory: Path,
        val cordappsDirectory: Path? = null,
        var user: String = "",
        var password: String = "",
        val hostAndPort: NetworkHostAndPort,
        val ssl: ClientRpcSslOptions? = null,
        val nodeSslConfig: SSLConfiguration? = null,
        val sshdPort: Int? = null,
        val sshHostKeyDirectory: Path? = null,
        val noLocalShell: Boolean = false) {
    companion object {
        const val SSH_PORT = 2222
        const val COMMANDS_DIR = "shell-commands"
        const val CORDAPPS_DIR = "cordapps"
        const val SSHD_HOSTKEY_DIR = "ssh"
    }
}
