package net.corda.node.services.config.shell

import net.corda.core.internal.div
import net.corda.node.internal.clientSslOptionsCompatibleWith
import net.corda.node.services.config.NodeConfiguration

private const val COMMANDS_DIR = "shell-commands"
private const val CORDAPPS_DIR = "cordapps"
private const val SSHD_HOSTKEY_DIR = "ssh"

//re-packs data to Shell specific classes
fun NodeConfiguration.toShellConfigMap() = mapOf(
        "commandsDirectory" to this.baseDirectory / COMMANDS_DIR,
        "cordappsDirectory" to this.baseDirectory.toString() / CORDAPPS_DIR,
        "user" to INTERNAL_SHELL_USER,
        "password" to internalShellPassword,
        "permissions" to internalShellPermissions(!this.localShellUnsafe),
        "localShellAllowExitInSafeMode" to this.localShellAllowExitInSafeMode,
        "localShellUnsafe" to this.localShellUnsafe,
        "hostAndPort" to this.rpcOptions.address,
        "ssl" to clientSslOptionsCompatibleWith(this.rpcOptions),
        "sshdPort" to this.sshd?.port,
        "sshHostKeyDirectory" to this.baseDirectory / SSHD_HOSTKEY_DIR,
        "noLocalShell" to this.noLocalShell
)
