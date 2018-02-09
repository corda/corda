package net.corda.testing.common.internal

import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import java.time.Instant

fun testNetworkParameters(
        notaries: List<NotaryInfo>,
        minimumPlatformVersion: Int = 1,
        modifiedTime: Instant = Instant.now(),
        maxMessageSize: Int = 10485760,
        maxTransactionSize: Int = 40000,
        epoch: Int = 1
): NetworkParameters {
    return NetworkParameters(
            minimumPlatformVersion = minimumPlatformVersion,
            notaries = notaries,
            modifiedTime = modifiedTime,
            maxMessageSize = maxMessageSize,
            maxTransactionSize = maxTransactionSize,
            epoch = epoch
    )
}