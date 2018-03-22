package net.corda.tools.shell

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.config.RevocationCheckConfig
import net.corda.nodeapi.internal.config.SSLConfiguration
import java.nio.file.Path
import java.nio.file.Paths

data class ShellConfiguration(
        val commandsDirectory: Path,
        val cordappsDirectory: Path? = null,
        var user: String = "",
        var password: String = "",
        val hostAndPort: NetworkHostAndPort,
        val ssl: ShellSslOptions? = null,
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

//TODO: sslKeystore -> it's a path not the keystore itself.
//TODO: trustStoreFile -> it's a path not the file itself.
data class ShellSslOptions(override val sslKeystore: Path,
                           override val keyStorePassword: String,
                           override val trustStoreFile: Path,
                           override val trustStorePassword: String,
                           override val revocationCheckConfig: RevocationCheckConfig) : SSLConfiguration {
    override val certificatesDirectory: Path get() = Paths.get("")
}