package net.corda.testing.common.internal

import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.days
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

@JvmOverloads
fun testNetworkParameters(
        notaries: List<NotaryInfo> = emptyList(),
        minimumPlatformVersion: Int = 1,
        modifiedTime: Instant = Instant.now(),
        maxMessageSize: Int = 10485760,
        // TODO: Make this configurable and consistence across driver, bootstrapper, demobench and NetworkMapServer
        maxTransactionSize: Int = maxMessageSize * 50,
        whitelistedContractImplementations: Map<String, List<AttachmentId>> = emptyMap(),
        epoch: Int = 1,
        eventHorizon: Duration = 30.days,
        packageOwnership: Map<String, PublicKey> = emptyMap()
): NetworkParameters {
    return NetworkParameters(
            minimumPlatformVersion = minimumPlatformVersion,
            notaries = notaries,
            maxMessageSize = maxMessageSize,
            maxTransactionSize = maxTransactionSize,
            modifiedTime = modifiedTime,
            epoch = epoch,
            whitelistedContractImplementations = whitelistedContractImplementations,
            eventHorizon = eventHorizon,
            packageOwnership = packageOwnership
    )
}

/**
 * Includes the specified notary [party] in the network parameter whitelist to ensure transactions verify.
 * If used when actual notarisation flows are involved, the right notary type (validating/non-validating) should be specified.
 *
 * Note that it returns new network parameters with a different hash.
 */
fun NetworkParameters.addNotary(party: Party, validating: Boolean = true): NetworkParameters {
    val notaryInfo = NotaryInfo(party, validating)
    val notaryList = notaries + notaryInfo
    return copy(notaries = notaryList)
}

