package net.corda.testing.common.internal

import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import java.time.Instant

fun testNetworkParameters(
        notaries: List<NotaryInfo> = emptyList(),
        minimumPlatformVersion: Int = 1,
        modifiedTime: Instant = Instant.now(),
        maxMessageSize: Int = 10485760,
        // TODO: Make this configurable and consistence across driver, bootstrapper, demobench and NetworkMapServer
        maxTransactionSize: Int = maxMessageSize,
        whitelistedContractImplementations: Map<String, List<AttachmentId>> = emptyMap(),
        epoch: Int = 1
): NetworkParameters {
    return NetworkParameters(
            minimumPlatformVersion = minimumPlatformVersion,
            notaries = notaries,
            maxMessageSize = maxMessageSize,
            maxTransactionSize = maxTransactionSize,
            whitelistedContractImplementations = whitelistedContractImplementations,
            modifiedTime = modifiedTime,
            epoch = epoch
    )
}