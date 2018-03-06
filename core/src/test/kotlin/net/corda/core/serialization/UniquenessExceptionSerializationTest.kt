/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.serialization

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.UniquenessException
import net.corda.core.node.services.UniquenessProvider
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class UniquenessExceptionSerializationTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun testSerializationRoundTrip() {
        val txhash = SecureHash.randomSHA256()
        val txHash2 = SecureHash.randomSHA256()
        val dummyParty = Party(CordaX500Name("Dummy", "Madrid", "ES"), generateKeyPair().public)
        val stateHistory: Map<StateRef, UniquenessProvider.ConsumingTx> = mapOf(StateRef(txhash, 0) to UniquenessProvider.ConsumingTx(txHash2, 1, dummyParty))
        val conflict = UniquenessProvider.Conflict(stateHistory)
        val instance = UniquenessException(conflict)

        val instanceOnTheOtherSide = instance.serialize().deserialize()

        assertEquals(instance.error, instanceOnTheOtherSide.error)
    }
}