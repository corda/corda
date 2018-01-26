@file:JvmName("CordformUtils")

package net.corda.testing.node.internal.demorun

import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.cordform.RpcSettings
import net.corda.cordform.SslOptions
import net.corda.core.identity.CordaX500Name
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.config.toConfig
import net.corda.testing.node.User

fun CordformDefinition.node(configure: CordformNode.() -> Unit) {
    addNode { cordformNode -> cordformNode.configure() }
}

fun CordformNode.name(name: CordaX500Name) = name(name.toString())

fun CordformNode.rpcUsers(vararg users: User) {
    rpcUsers = users.map { it.toConfig().root().unwrapped() }
}

fun CordformNode.notary(notaryConfig: NotaryConfig) {
    notary = notaryConfig.toConfig().root().unwrapped()
}

fun CordformNode.rpcSettings(configure: RpcSettings.() -> Unit) {
    RpcSettings().also(configure).also(this::rpcSettings)
}

fun RpcSettings.ssl(configure: SslOptions.() -> Unit) {
    SslOptions().also(configure).also(this::ssl)
}