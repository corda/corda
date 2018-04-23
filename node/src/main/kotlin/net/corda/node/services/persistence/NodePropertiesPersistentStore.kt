/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.persistence

import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.NodePropertiesStore
import net.corda.node.services.api.NodePropertiesStore.FlowsDrainingModeOperations
import net.corda.node.utilities.PersistentMap
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.slf4j.Logger
import rx.subjects.PublishSubject
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * Simple node properties key value store in DB.
 */
class NodePropertiesPersistentStore(readPhysicalNodeId: () -> String, persistence: CordaPersistence) : NodePropertiesStore {

    private companion object {
        val logger = loggerFor<NodePropertiesStore>()
    }

    override val flowsDrainingMode: FlowsDrainingModeOperations = FlowsDrainingModeOperationsImpl(readPhysicalNodeId, persistence, logger)

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}properties")
    class DBNodeProperty(
            @Id
            @Column(name = "property_key")
            val key: String = "",

            @Column(name = "property_value")
            var value: String? = ""
    ) : Serializable
}

private class FlowsDrainingModeOperationsImpl(readPhysicalNodeId: () -> String, private val persistence: CordaPersistence, logger: Logger) : FlowsDrainingModeOperations {

    private val nodeSpecificFlowsExecutionModeKey = "${readPhysicalNodeId()}_flowsExecutionMode"

    init {
        logger.debug { "Node's flow execution mode property key: $nodeSpecificFlowsExecutionModeKey" }
    }

    private val map = PersistentMap({ key -> key }, { entity -> entity.key to entity.value!! }, NodePropertiesPersistentStore::DBNodeProperty, NodePropertiesPersistentStore.DBNodeProperty::class.java)

    override val values = PublishSubject.create<Pair<Boolean, Boolean>>()!!

    override fun setEnabled(enabled: Boolean) {

        var oldValue: Boolean? = null
        persistence.transaction {
            oldValue = map.put(nodeSpecificFlowsExecutionModeKey, enabled.toString())?.toBoolean() ?: false
        }
        values.onNext(oldValue!! to enabled)
    }

    override fun isEnabled(): Boolean {

        return persistence.transaction {
            map[nodeSpecificFlowsExecutionModeKey]?.toBoolean() ?: false
        }
    }
}