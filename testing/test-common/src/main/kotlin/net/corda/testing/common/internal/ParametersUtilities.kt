package net.corda.testing.common.internal

import net.corda.core.contracts.WhitelistedByZoneAttachmentConstraint.whitelistAllContractsForTest
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import java.time.Instant

object ParametersUtilities {

    @JvmStatic
    @JvmOverloads
    fun testNetworkParameters(
            notaries: List<NotaryInfo>,
            minimumPlatformVersion: Int = 1,
            modifiedTime: Instant = Instant.now(),
            maxMessageSize: Int = 10485760,
            // TODO: Make this configurable and consistence across driver, bootstrapper, demobench and NetworkMapServer
            maxTransactionSize: Int = Int.MAX_VALUE,
            epoch: Int = 1
    ): NetworkParameters {
        return NetworkParameters(
                minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                modifiedTime = modifiedTime,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                epoch = epoch,
                whitelistedContractImplementations = whitelistAllContractsForTest
        )
    }
}
