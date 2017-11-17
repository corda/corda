package net.corda.testing.common.internal

import net.corda.core.utilities.days
import net.corda.nodeapi.internal.NetworkParameters
import net.corda.nodeapi.internal.NotaryInfo
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