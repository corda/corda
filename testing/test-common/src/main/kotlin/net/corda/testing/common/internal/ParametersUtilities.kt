package net.corda.testing.common.internal

import com.google.common.hash.Hashing
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.hexToByteArray
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.network.NotaryInfo
import java.nio.charset.StandardCharsets
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
            epoch = epoch,
            whitelistedContractImplementations = getMockWhitelistedContractImplementations()
    )
}
val acceptAll = mapOf("all" to listOf(SecureHash.zeroHash, SecureHash.allOnesHash))

fun getMockWhitelistedContractImplementations() = acceptAll