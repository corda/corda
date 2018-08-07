package net.corda.configsample

import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.verification.TestCommsFlowInitiator
import org.junit.Assert
import org.junit.Test

class TestCommsFlowInitiatorTest {

    val ALICE = TestIdentity(ALICE_NAME, 70)
    val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 12)
    val DUMMY_BANK_A = TestIdentity(DUMMY_BANK_A_NAME, 3)

    @Test
    fun `should allow all node infos through if no x500 is passed`() {
        val testCommsFlowInitiator = TestCommsFlowInitiator()

        Assert.assertTrue(testCommsFlowInitiator.matchesX500(ALICE.party))
        Assert.assertTrue(testCommsFlowInitiator.matchesX500(NOTARY.party))
        Assert.assertTrue(testCommsFlowInitiator.matchesX500(DUMMY_BANK_A.party))
    }

    @Test
    fun `should allow only specified x500 if no x500 is passed`() {
        val testCommsFlowInitiator = TestCommsFlowInitiator(ALICE_NAME)

        Assert.assertTrue(testCommsFlowInitiator.matchesX500(ALICE.party))
        Assert.assertFalse(testCommsFlowInitiator.matchesX500(NOTARY.party))
        Assert.assertFalse(testCommsFlowInitiator.matchesX500(DUMMY_BANK_A.party))
    }
}