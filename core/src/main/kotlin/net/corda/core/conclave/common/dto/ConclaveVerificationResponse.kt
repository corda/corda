package net.corda.core.conclave.common.dto

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ConclaveVerificationResponse(
        val verificationStatus: VerificationStatus,
        val verificationErrorMessage: String?
)