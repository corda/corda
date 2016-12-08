package net.corda.node.services.keys

import net.corda.core.ThreadBox
import net.corda.core.crypto.generateKeyPair
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.utilities.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * A persistent re-implementation of [E2ETestKeyManagementService] to support node re-start.
 *
 * This is not the long-term implementation.  See the list of items in the above class.
 *
 * This class needs database transactions to be in-flight during method calls and init.
 */
class PersistentKeyManagementService(initialKeys: Set<KeyPair>) : SingletonSerializeAsToken(), KeyManagementService {

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

    override val keys: Map<PublicKey, PrivateKey> get() = mutex.locked { HashMap(keys) }

    override fun freshKey(): KeyPair {
        val keyPair = generateKeyPair()
        mutex.locked {
            keys[keyPair.public] = keyPair.private
        }
        return keyPair
    }
}
