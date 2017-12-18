package net.corda.testing.internal

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.testing.getTestPartyAndCertificate
import java.security.PrivateKey

class TestNodeInfoBuilder {
    private val identitiesAndPrivateKeys = ArrayList<Pair<PartyAndCertificate, PrivateKey>>()

    fun addIdentity(name: CordaX500Name): Pair<PartyAndCertificate, PrivateKey> {
        val identityKeyPair = Crypto.generateKeyPair()
        val identity = getTestPartyAndCertificate(name, identityKeyPair.public)
        return Pair(identity, identityKeyPair.private).also {
            identitiesAndPrivateKeys += it
        }
    }

    fun build(serial: Long = 1): NodeInfo {
        return NodeInfo(
                listOf(NetworkHostAndPort("my.${identitiesAndPrivateKeys[0].first.party.name.organisation}.com", 1234)),
                identitiesAndPrivateKeys.map { it.first },
                1,
                serial
        )
    }

    fun buildWithSigned(serial: Long = 1): Pair<NodeInfo, SignedNodeInfo> {
        val nodeInfo = build(serial)
        val privateKeys = identitiesAndPrivateKeys.map { it.second }
        return Pair(nodeInfo, nodeInfo.signWith(privateKeys))
    }

    fun reset() {
        identitiesAndPrivateKeys.clear()
    }
}

fun createNodeInfoAndSigned(vararg names: CordaX500Name, serial: Long = 1): Pair<NodeInfo, SignedNodeInfo> {
    val nodeInfoBuilder = TestNodeInfoBuilder()
    names.forEach { nodeInfoBuilder.addIdentity(it) }
    return nodeInfoBuilder.buildWithSigned(serial)
}

fun NodeInfo.signWith(keys: List<PrivateKey>): SignedNodeInfo {
    val serialized = serialize()
    val signatures = keys.map { it.sign(serialized.bytes) }
    return SignedNodeInfo(serialized, signatures)
}
