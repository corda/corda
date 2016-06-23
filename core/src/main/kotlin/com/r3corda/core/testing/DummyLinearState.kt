package com.r3corda.core.testing

import com.r3corda.core.contracts.Contract
import com.r3corda.core.contracts.LinearState
import com.r3corda.core.crypto.SecureHash
import java.security.PublicKey
import java.util.*

class DummyLinearState(
        override val thread: SecureHash = SecureHash.randomSHA256(),
        override val contract: Contract = AlwaysSucceedContract(),
        override val participants: List<PublicKey> = listOf(),
        val nonce: SecureHash = SecureHash.randomSHA256()) : LinearState {

    override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
        return participants.any { ourKeys.contains(it) }
    }
}
