package net.corda.bn.states

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals

class GroupStateTest {

    @Test(timeout = 300_000)
    fun `converting group state to business network group should work`() {
        val party = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB")).party
        val groupState = GroupState(networkId = "network-id", name = "name", participants = listOf(party))

        groupState.toBusinessNetworkGroup().apply {
            assertEquals(groupState.networkId, networkId)
            assertEquals(groupState.name, name)
            assertEquals(groupState.issued, issued)
            assertEquals(groupState.modified, modified)
            assertEquals(groupState.linearId, groupId)
            assertEquals(groupState.participants, participants)
        }
    }
}