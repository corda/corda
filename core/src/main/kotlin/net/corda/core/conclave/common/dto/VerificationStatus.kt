package net.corda.core.conclave.common.dto

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class VerificationStatus {
    SUCCESS, VERIFICATION_ERROR
}