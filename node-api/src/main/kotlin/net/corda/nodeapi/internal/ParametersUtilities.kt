package net.corda.nodeapi.internal

import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.days
import java.time.Instant

fun testNetworkParameters(notaries: List<NotaryInfo>): NetworkParameters {
    return NetworkParameters(
            minimumPlatformVersion = 1,
            notaries = notaries,
            modifiedTime = Instant.now(),
            eventHorizon = 10000.days,
            maxMessageSize = 40000,
            maxTransactionSize = 40000,
            epoch = 1
    )
}