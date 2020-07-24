package net.corda.bn.states

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.bn.MembershipIdentity
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals

class MembershipStateTest {

    @Test(timeout = 300_000)
    fun `converting membership state to business network membership should work`() {
        val party = TestIdentity(CordaX500Name.parse("O=Member,L=London,C=GB")).party
        val membershipState = MembershipState(
                identity = MembershipIdentity(party),
                networkId = "network-id",
                status = MembershipStatus.ACTIVE,
                roles = setOf(),
                participants = listOf(party)
        )

        membershipState.toBusinessNetworkMembership().apply {
            assertEquals(membershipState.identity, identity)
            assertEquals(membershipState.networkId, networkId)
            assertEquals(membershipState.status, status)
            assertEquals(membershipState.roles, roles)
            assertEquals(membershipState.issued, issued)
            assertEquals(membershipState.modified, modified)
            assertEquals(membershipState.linearId, membershipId)
            assertEquals(membershipState.participants, participants)
        }
    }
}