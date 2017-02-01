package net.corda.node.services.network

import net.corda.core.ThreadBox
import net.corda.core.crypto.Party
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.utilities.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.util.Collections.synchronizedMap

/**
 * A network map service backed by a database to survive restarts of the node hosting it.
 *
 * Majority of the logic is inherited from [AbstractNetworkMapService].
 *
 * This class needs database transactions to be in-flight during method calls and init, otherwise it will throw
 * exceptions.
 */
class PersistentNetworkMapService(services: ServiceHubInternal) : AbstractNetworkMapService(services) {
    private object Table : JDBCHashedTable("${NODE_DATABASE_PREFIX}network_map_nodes") {
        val nodeParty = party("node_party_name", "node_party_key")
        val registrationInfo = blob("node_registration_info")
    }

    override val registeredNodes: MutableMap<Party.Full, NodeRegistrationInfo> = synchronizedMap(object : AbstractJDBCHashMap<Party.Full, NodeRegistrationInfo, Table>(Table, loadOnInit = true) {
        override fun keyFromRow(row: ResultRow): Party.Full = Party.Full(row[table.nodeParty.name], row[table.nodeParty.owningKey])

        override fun valueFromRow(row: ResultRow): NodeRegistrationInfo = deserializeFromBlob(row[table.registrationInfo])

        override fun addKeyToInsert(insert: InsertStatement, entry: Map.Entry<Party.Full, NodeRegistrationInfo>, finalizables: MutableList<() -> Unit>) {
            insert[table.nodeParty.name] = entry.key.name
            insert[table.nodeParty.owningKey] = entry.key.owningKey
        }

        override fun addValueToInsert(insert: InsertStatement, entry: Map.Entry<Party.Full, NodeRegistrationInfo>, finalizables: MutableList<() -> Unit>) {
            insert[table.registrationInfo] = serializeToBlob(entry.value, finalizables)
        }
    })

    override val subscribers = ThreadBox(JDBCHashMap<SingleMessageRecipient, LastAcknowledgeInfo>("${NODE_DATABASE_PREFIX}network_map_subscribers", loadOnInit = true))

    init {
        // Initialise the network map version with the current highest persisted version, or zero if there are no entries.
        _mapVersion.set(registeredNodes.values.map { it.mapVersion }.max() ?: 0)
        setup()
    }
}
