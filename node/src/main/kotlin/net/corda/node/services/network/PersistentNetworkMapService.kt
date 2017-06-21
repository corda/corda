package net.corda.node.services.network

import net.corda.core.ThreadBox
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.utilities.*
import org.bouncycastle.asn1.x500.X500Name
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
class PersistentNetworkMapService(services: ServiceHubInternal, minimumPlatformVersion: Int)
    : AbstractNetworkMapService(services, minimumPlatformVersion) {

    private object Table : JDBCHashedTable("${NODE_DATABASE_PREFIX}network_map_nodes") {
        val nodeParty = partyAndCertificate("node_party_name", "node_party_key", "node_party_certificate", "node_party_path")
        val registrationInfo = blob("node_registration_info")
    }

    override val nodeRegistrations: MutableMap<PartyAndCertificate, NodeRegistrationInfo> = synchronizedMap(object : AbstractJDBCHashMap<PartyAndCertificate, NodeRegistrationInfo, Table>(Table, loadOnInit = true) {
        // TODO: We should understand an X500Name database field type, rather than manually doing the conversion ourselves
        override fun keyFromRow(row: ResultRow): PartyAndCertificate = PartyAndCertificate(X500Name(row[table.nodeParty.name]), row[table.nodeParty.owningKey],
                row[table.nodeParty.certificate], row[table.nodeParty.certPath])

        override fun valueFromRow(row: ResultRow): NodeRegistrationInfo = deserializeFromBlob(row[table.registrationInfo])

        override fun addKeyToInsert(insert: InsertStatement, entry: Map.Entry<PartyAndCertificate, NodeRegistrationInfo>, finalizables: MutableList<() -> Unit>) {
            insert[table.nodeParty.name] = entry.key.name.toString()
            insert[table.nodeParty.owningKey] = entry.key.owningKey
            insert[table.nodeParty.certPath] = entry.key.certPath
            insert[table.nodeParty.certificate] = entry.key.certificate
        }

        override fun addValueToInsert(insert: InsertStatement, entry: Map.Entry<PartyAndCertificate, NodeRegistrationInfo>, finalizables: MutableList<() -> Unit>) {
            insert[table.registrationInfo] = serializeToBlob(entry.value, finalizables)
        }
    })

    override val subscribers = ThreadBox(JDBCHashMap<SingleMessageRecipient, LastAcknowledgeInfo>("${NODE_DATABASE_PREFIX}network_map_subscribers", loadOnInit = true))

    init {
        // Initialise the network map version with the current highest persisted version, or zero if there are no entries.
        _mapVersion.set(nodeRegistrations.values.map { it.mapVersion }.max() ?: 0)
        setup()
    }
}
