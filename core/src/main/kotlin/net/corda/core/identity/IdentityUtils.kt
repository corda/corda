@file:JvmName("IdentityUtils")

package net.corda.core.identity

import net.corda.core.internal.toMultiMap
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import java.security.PublicKey

/**
 * Group each [PublicKey] by the well known party using the [ServiceHub.identityService], in preparation for
 * creating [FlowSession]s, for example.
 *
 * @param publicKeys the [PublicKey]s to group.
 * @param ignoreUnrecognisedParties if this is false, throw an exception if some of the [PublicKey]s cannot be mapped
 * to a [Party].
 * @return a map of well known [Party] to associated [PublicKey]s.
 */
@Throws(IllegalArgumentException::class)
fun groupPublicKeysByWellKnownParty(serviceHub: ServiceHub, publicKeys: Collection<PublicKey>, ignoreUnrecognisedParties: Boolean): Map<Party, List<PublicKey>> =
        groupAbstractPartyByWellKnownParty(serviceHub, publicKeys.map { AnonymousParty(it) }, ignoreUnrecognisedParties).mapValues { it.value.map { it.owningKey } }

/**
 * Group each [PublicKey] by the well known party using the [ServiceHub.identityService], in preparation for
 * creating [FlowSession]s, for example.  Throw an exception if some of the [PublicKey]s cannot be mapped
 * to a [Party].
 *
 * @param publicKeys the [PublicKey]s to group.
 * @return a map of well known [Party] to associated [PublicKey]s.
 */
// Cannot use @JvmOverloads in interface
@Throws(IllegalArgumentException::class)
fun groupPublicKeysByWellKnownParty(serviceHub: ServiceHub, publicKeys: Collection<PublicKey>): Map<Party, List<PublicKey>> = groupPublicKeysByWellKnownParty(serviceHub, publicKeys, false)

/**
 * Group each [AbstractParty] by the well known party using the [ServiceHub.identityService], in preparation for
 * creating [FlowSession]s, for example.
 *
 * @param parties the [AbstractParty]s to group.
 * @param ignoreUnrecognisedParties if this is false, throw an exception if some of the [AbstractParty]s cannot be mapped
 * to a [Party].
 * @return a map of well known [Party] to associated [AbstractParty]s.
 */
@Throws(IllegalArgumentException::class)
fun groupAbstractPartyByWellKnownParty(serviceHub: ServiceHub, parties: Collection<AbstractParty>, ignoreUnrecognisedParties: Boolean): Map<Party, List<AbstractParty>> {
    val partyToPublicKey: Iterable<Pair<Party, AbstractParty>> = parties.mapNotNull {
        (serviceHub.identityService.wellKnownPartyFromAnonymous(it) ?: if (ignoreUnrecognisedParties) return@mapNotNull null else throw IllegalArgumentException("Could not find Party for $it")) to it
    }
    return partyToPublicKey.toMultiMap()
}

/**
 * Group each [AbstractParty] by the well known party using the [ServiceHub.identityService], in preparation for
 * creating [FlowSession]s, for example. Throw an exception if some of the [AbstractParty]s cannot be mapped
 * to a [Party].
 *
 * @param parties the [AbstractParty]s to group.
 * @return a map of well known [Party] to associated [AbstractParty]s.
 */
// Cannot use @JvmOverloads in interface
@Throws(IllegalArgumentException::class)
fun groupAbstractPartyByWellKnownParty(serviceHub: ServiceHub, parties: Collection<AbstractParty>): Map<Party, List<AbstractParty>> {
    return groupAbstractPartyByWellKnownParty(serviceHub, parties, false)
}

/**
 * Remove this node from a map of well known [Party]s.
 *
 * @return a new copy of the map, with he well known [Party] for this node removed.
 */
fun <T> excludeHostNode(serviceHub: ServiceHub, map: Map<Party, T>): Map<Party, T> = map.filterKeys { !serviceHub.myInfo.isLegalIdentity(it) }

/**
 * Remove the [Party] associated with the notary of a [SignedTransaction] from the a map of [Party]s.  It is a no-op
 * if the notary is null.
 *
 * @return a new copy of the map, with the well known [Party] for the notary removed.
 */
fun <T> excludeNotary(map: Map<Party, T>, stx: SignedTransaction): Map<Party, T> = map.filterKeys { it != stx.notary }
