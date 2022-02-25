package com.r3.conclave.cordapp.common.dto

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class VerificationStatus {
    SUCCESS, VERIFICATION_ERROR
}