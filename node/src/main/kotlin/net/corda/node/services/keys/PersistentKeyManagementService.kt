package net.corda.node.services.keys

import net.corda.core.ThreadBox
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.keys
import net.corda.core.crypto.sign
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.utilities.*
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.operator.ContentSigner
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertPath

/**
 * A persistent re-implementation of [E2ETestKeyManagementService] to support node re-start.
 *
 * This is not the long-term implementation.  See the list of items in the above class.
 *
 * This class needs database transactions to be in-flight during method calls and init.
 */
class PersistentKeyManagementService(val identityService: IdentityService,
                                     initialKeys: Set<KeyPair>) : SingletonSerializeAsToken(), KeyManagementService {

    private object Table : JDBCHashedTable("${NODE_DATABASE_PREFIX}our_key_pairs") {
        val publicKey = publicKey("public_key")
        val privateKey = blob("private_key")
    }

    private class InnerState {
        val keys = object : AbstractJDBCHashMap<PublicKey, PrivateKey, Table>(Table, loadOnInit = false) {
            override fun keyFromRow(row: ResultRow): PublicKey = row[table.publicKey]

            override fun valueFromRow(row: ResultRow): PrivateKey = deserializeFromBlob(row[table.privateKey])

            override fun addKeyToInsert(insert: InsertStatement, entry: Map.Entry<PublicKey, PrivateKey>, finalizables: MutableList<() -> Unit>) {
                insert[table.publicKey] = entry.key
            }

            override fun addValueToInsert(insert: InsertStatement, entry: Map.Entry<PublicKey, PrivateKey>, finalizables: MutableList<() -> Unit>) {
                insert[table.privateKey] = serializeToBlob(entry.value, finalizables)
            }
        }
    }

    private val mutex = ThreadBox(InnerState())

    init {
        mutex.locked {
            keys.putAll(initialKeys.associate { Pair(it.public, it.private) })
        }
    }

    override val keys: Set<PublicKey> get() = mutex.locked { keys.keys }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        return mutex.locked { candidateKeys.filter { it in this.keys } }
    }

    override fun freshKey(): PublicKey {
        val keyPair = generateKeyPair()
        mutex.locked {
            keys[keyPair.public] = keyPair.private
        }
        return keyPair.public
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): Pair<X509CertificateHolder, CertPath> {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey), revocationEnabled)
    }

    private fun getSigner(publicKey: PublicKey): ContentSigner  = getSigner(getSigningKeyPair(publicKey))

    private fun getSigningKeyPair(publicKey: PublicKey): KeyPair {
        return mutex.locked {
            val pk = publicKey.keys.first { keys.containsKey(it) }
            KeyPair(pk, keys[pk]!!)
        }
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        val keyPair = getSigningKeyPair(publicKey)
        val signature = keyPair.sign(bytes)
        return signature
    }

}
