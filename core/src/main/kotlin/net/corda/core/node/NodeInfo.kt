package net.corda.core.node

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.NetworkHostAndPort

/**
 * Information about a network node that acts on behalf of some party. NodeInfos can be found via the network map
 * cache, accessible from a [net.corda.core.node.services.NetworkMapCache]. They are also available via RPC
 * using the [net.corda.core.messaging.CordaRPCOps.networkMapSnapshot] method.
 *
 * @property addresses An ordered list of IP addresses/hostnames where the node can be contacted.
 * @property legalIdentitiesAndCerts A non-empty list, where the first identity is assumed to be the default identity of the node.
 * @property platformVersion An integer representing the set of protocol features the node supports. See the docsite
 *           for information on how the platform is versioned.
 * @property serial An arbitrary number incremented each time the NodeInfo is changed. This is analogous to the same
 *           concept in DNS.
 */
@CordaSerializable
data class NodeInfo(val addresses: List<NetworkHostAndPort>,
                    val legalIdentitiesAndCerts: List<PartyAndCertificate>,
                    val platformVersion: Int,
                    val serial: Long
) {
    // TODO We currently don't support multi-IP/multi-identity nodes, we only left slots in the data structures.
    init {
        require(legalIdentitiesAndCerts.isNotEmpty()) { "Node should have at least one legal identity" }
    }

    @Transient private var _legalIdentities: List<Party>? = null

    /**
     * An ordered list of legal identities supported by this node. The node will always have at least one, so if you
     * are porting code from earlier versions of Corda that expected a single party per node, just use the first item
     * in the returned list.
     */
    val legalIdentities: List<Party>
        get() {
            return _legalIdentities ?: legalIdentitiesAndCerts.map { it.party }.also { _legalIdentities = it }
        }

    /** Returns true if [party] is one of the identities of this node, else false. */
    fun isLegalIdentity(party: Party): Boolean = party in legalIdentities

    fun chooseIdentity(name: CordaX500Name): Party  {
        val identity = legalIdentitiesAndCerts.singleOrNull { it.name == name } ?: throw IllegalArgumentException("Node does not have an identity \"$name\"")
        return identity.party
    }
}
