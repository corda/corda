package net.corda.core.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.Destination
import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.OpaqueBytes
import java.security.PublicKey
import java.security.cert.X509Certificate

/**
 * The [Party] class represents an entity on the network, which is typically identified by a legal [name] and public key
 * that it can sign transactions under. As parties may use multiple keys for signing and, for example, have offline backup
 * keys, the "public key" of a party can be represented by a composite construct – a [CompositeKey], which combines multiple
 * cryptographic public key primitives into a tree structure.
 *
 * For example: Alice has two key pairs (pub1/priv1 and pub2/priv2), and wants to be able to sign transactions with either of them.
 * Her advertised [Party] then has a legal X.500 [name] "CN=Alice Corp,O=Alice Corp,L=London,C=GB" and an [owningKey]
 * "pub1 or pub2".
 *
 * [Party] is also used for service identities. E.g. Alice may also be running an interest rate oracle on her Corda node,
 * which requires a separate signing key (and an identifying name). Services can also be distributed – run by a coordinated
 * cluster of Corda nodes. A [Party] representing a distributed service will use a composite key containing all
 * individual cluster nodes' public keys. Each of the nodes in the cluster will advertise the same group [Party].
 *
 * Note that equality is based solely on the owning key.
 *
 * ### Flow sessions
 *
 * Communication with other parties is done using the flow framework with the [FlowLogic.initiateFlow] method. Message routing is done by
 * using the network map to look up the connectivity details pertaining to the [Party].
 *
 * @see CompositeKey
 */
class Party(val name: CordaX500Name, owningKey: PublicKey) : Destination, AbstractParty(owningKey) {
    constructor(certificate: X509Certificate)
            : this(CordaX500Name.build(certificate.subjectX500Principal), Crypto.toSupportedPublicKey(certificate.publicKey))

    override fun nameOrNull(): CordaX500Name = name
    fun anonymise(): AnonymousParty = AnonymousParty(owningKey)
    override fun ref(bytes: OpaqueBytes): PartyAndReference = PartyAndReference(this, bytes)
    override fun toString() = name.toString()
    fun description() = "$name (owningKey = ${owningKey.toStringShort()})"

    companion object {
        /**
         * Factory method to be used in preference to the constructor.
         */
        fun create(name: CordaX500Name, owningKey: PublicKey): Party = interner.intern(Party(name, owningKey))

        /**
         * Factory method to be used in preference to the constructor.
         */
        fun create(certificate: X509Certificate): Party = interner.intern(Party(certificate))
    }
}
