package net.corda.node.services.persistence

import net.corda.core.utilities.loggerFor
import net.corda.node.events.Event
import net.corda.node.events.FlowsDrainingModeSetEvent
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.utilities.PersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * Simple node properties key value store in DB.
 */
class NodePropertiesPersistentStore(
        readPhysicalNodeId: () -> String,
        private val persistence: CordaPersistence,
        private val publishEvent: (Event) -> Unit) : NodePropertiesStore {

    private companion object {

        val logger = loggerFor<NodePropertiesStore>()
        const val flowsExecutionModeKey = "flowsExecutionMode"
    }

    private val nodeSpecificFlowsExecutionModeKey = "${readPhysicalNodeId()}_$flowsExecutionModeKey"

    init {
        logger.debug("Node's flow execution mode property key: $nodeSpecificFlowsExecutionModeKey")
    }

    private val map = PersistentMap({ key -> key }, { entity -> entity.key to entity.value!! }, ::DBNodeProperty, DBNodeProperty::class.java)

    override fun setFlowsDrainingModeEnabled(enabled: Boolean) {
        persistence.transaction {
            map.put(nodeSpecificFlowsExecutionModeKey, enabled.toString())
        }
        publishEvent(FlowsDrainingModeSetEvent(enabled))
    }

    override fun isFlowsDrainingModeEnabled(): Boolean {
        return persistence.transaction {
            map[nodeSpecificFlowsExecutionModeKey]?.toBoolean() ?: false
        }
    }


    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}properties")
    class DBNodeProperty(
            @Id
            @Column(name = "key")
            val key: String = "",

            @Column(name = "value")
            var value: String? = ""
    )
}