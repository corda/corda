package com.r3corda.node.services.network

import com.r3corda.core.ThreadBox
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.utilities.JDBCHashMap
import java.util.*

/**
 * A network map service backed by a database to survive restarts of the node hosting it.
 *
 * Majority of the logic is inherited from [AbstractNetworkMapService].
 *
 * This class needs database transactions to be in-flight during method calls and init, otherwise it will throw
 * exceptions.
 */
class PersistentNetworkMapService(services: ServiceHubInternal) : AbstractNetworkMapService(services) {

    override val registeredNodes: MutableMap<Party, NodeRegistrationInfo> = Collections.synchronizedMap(JDBCHashMap("network_map_nodes", loadOnInit = true))

    override val subscribers = ThreadBox(JDBCHashMap<SingleMessageRecipient, LastAcknowledgeInfo>("network_map_subscribers", loadOnInit = true))

    init {
        // Initialise the network map version with the current highest persisted version, or zero if there are no entries.
        _mapVersion.set(registeredNodes.values.map { it.mapVersion }.max() ?: 0)
        setup()
    }
}
