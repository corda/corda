package net.corda.core.serialization

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.UniquenessException
import net.corda.core.node.services.UniquenessProvider
import net.corda.testing.DUMMY_PARTY
import net.corda.testing.TestDependencyInjectionBase
import org.junit.Test
import kotlin.test.assertEquals

class UniquenessExceptionSerializationTest : TestDependencyInjectionBase() {

    @Test
    fun testSerializationRoundTrip() {
        val txhash = SecureHash.randomSHA256()
        val txHash2 = SecureHash.randomSHA256()
        val stateHistory: Map<StateRef, UniquenessProvider.ConsumingTx> = mapOf(StateRef(txhash, 0) to UniquenessProvider.ConsumingTx(txHash2, 1, DUMMY_PARTY))
        val conflict = UniquenessProvider.Conflict(stateHistory)
        val instance = UniquenessException(conflict)

        val instanceOnTheOtherSide = instance.serialize().deserialize()

        assertEquals(instance.error, instanceOnTheOtherSide.error)
    }
}