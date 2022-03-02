package net.corda.core.conclave.common.dto

import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

@CordaSerializable
data class ConclaveNetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: Array<NotaryInfo>,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        val modifiedTime: Instant,
        val epoch: Int,
        val whitelistedContractImplementations: Array<Pair<String, Array<AttachmentId>>>,
        val eventHorizon: Duration,
        val packageOwnership: Array<Pair<String, PublicKey>>
)
