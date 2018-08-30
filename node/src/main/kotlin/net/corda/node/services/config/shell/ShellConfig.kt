package net.corda.node.services.config.shell

import net.corda.core.internal.div
import net.corda.node.internal.extractRpcClientSslOptions
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.INTERNAL_SHELL_USER
import net.corda.tools.shell.ShellConfiguration
import net.corda.tools.shell.ShellConfiguration.Companion.COMMANDS_DIR
import net.corda.tools.shell.ShellConfiguration.Companion.CORDAPPS_DIR
import net.corda.tools.shell.ShellConfiguration.Companion.SSHD_HOSTKEY_DIR

//re-packs data to Shell specific classes
fun NodeConfiguration.toShellConfig() = ShellConfiguration(
        commandsDirectory = this.baseDirectory / COMMANDS_DIR,
        cordappsDirectory = this.baseDirectory.toString() / CORDAPPS_DIR,
        user = INTERNAL_SHELL_USER,
        password = INTERNAL_SHELL_USER,
        hostAndPort = this.rpcOptions.address,
        ssl = extractRpcClientSslOptions(this.rpcOptions),
        sshdPort = this.sshd?.port,
        sshHostKeyDirectory = this.baseDirectory / SSHD_HOSTKEY_DIR,
        noLocalShell = this.noLocalShell)
