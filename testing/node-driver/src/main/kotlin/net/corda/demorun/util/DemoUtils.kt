package net.corda.demorun.util

import net.corda.cordform.CordformDefinition
import net.corda.cordform.CordformNode
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.User
import org.bouncycastle.asn1.x500.X500Name

fun CordformDefinition.node(configure: CordformNode.() -> Unit) {
    addNode { cordformNode -> cordformNode.configure() }
}

fun CordformNode.name(name: CordaX500Name) = name(name.toString())

fun CordformNode.rpcUsers(vararg users: User) {
    rpcUsers = users.map { it.toMap() }
}

fun CordformNode.advertisedServices(vararg services: ServiceInfo) {
    advertisedServices = services.map { it.toString() }
}

fun CordformNode.notaryClusterAddresses(vararg addresses: NetworkHostAndPort) {
    notaryClusterAddresses = addresses.map { it.toString() }
}
