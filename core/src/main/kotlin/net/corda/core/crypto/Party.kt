package net.corda.core.crypto

import net.corda.core.contracts.PartyAndReference
import net.corda.core.serialization.OpaqueBytes
import java.security.PublicKey

/**
 * The [Party] class represents an entity on the network, which is typically identified by a legal [name] and public key
 * that it can sign transactions under. As parties may use multiple keys for signing and, for example, have offline backup
 * keys, the "public key" of a party is represented by a composite construct – a [CompositeKey], which combines multiple
 * cryptographic public key primitives into a tree structure.
 *
 * For example: Alice has two key pairs (pub1/priv1 and pub2/priv2), and wants to be able to sign transactions with either of them.
 * Her advertised [Party] then has a legal [name] "Alice" and an [owingKey] "pub1 or pub2".
 *
 * [Party] is also used for service identities. E.g. Alice may also be running an interest rate oracle on her Corda node,
 * which requires a separate signing key (and an identifying name). Services can also be distributed – run by a coordinated
 * cluster of Corda nodes. A [Party] representing a distributed service will use a composite key containing all
 * individual cluster nodes' public keys. Each of the nodes in the cluster will advertise the same group [Party].
 *
 * Note that equality is based solely on the owning key.
 *
 * @see CompositeKey
 */
class Party(val name: String, val owningKey: CompositeKey) {
    /** A helper constructor that converts the given [PublicKey] in to a [CompositeKey] with a single node */
    constructor(name: String, owningKey: PublicKey) : this(name, owningKey.composite)

    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other is Party && this.owningKey == other.owningKey
    override fun hashCode(): Int = owningKey.hashCode()
    override fun toString() = name

    fun ref(bytes: OpaqueBytes) = PartyAndReference(this, bytes)
    fun ref(vararg bytes: Byte) = ref(OpaqueBytes.of(*bytes))
}