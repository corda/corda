/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.artemis

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.LifecycleSupport
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl

interface ArtemisBroker : LifecycleSupport, AutoCloseable {
    val addresses: BrokerAddresses

    val serverControl: ActiveMQServerControl

    override fun close() = stop()
}

data class BrokerAddresses(val primary: NetworkHostAndPort, private val adminArg: NetworkHostAndPort?) {
    val admin = adminArg ?: primary
}