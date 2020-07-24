package net.corda.core.node.services.bn

import com.nhaarman.mockito_kotlin.mock
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessNetworkMembershipTest {

    private fun mockMembership(status: MembershipStatus = MembershipStatus.PENDING, roles: Set<BNRole> = emptySet()) = BusinessNetworkMembership(
            identity = MembershipIdentity(Party(CordaX500Name.parse("O=Member,L=London,C=GB"), Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public)),
            networkId = "",
            status = status,
            roles = roles,
            membershipId = UniqueIdentifier(),
            participants = mock()
    )

    @Test(timeout = 300_000)
    fun `membership state status helper methods should work`() {
        mockMembership(status = MembershipStatus.PENDING).apply {
            assertTrue(isPending())
            assertFalse(isActive())
            assertFalse(isSuspended())
        }
        mockMembership(status = MembershipStatus.ACTIVE).apply {
            assertFalse(isPending())
            assertTrue(isActive())
            assertFalse(isSuspended())
        }
        mockMembership(status = MembershipStatus.SUSPENDED).apply {
            assertFalse(isPending())
            assertFalse(isActive())
            assertTrue(isSuspended())
        }
    }

    @Test(timeout = 300_000)
    fun `membership state role helper methods should work`() {
        mockMembership(roles = setOf(BNORole())).apply {
            assertTrue(canActivateMembership())
            assertTrue(canSuspendMembership())
            assertTrue(canRevokeMembership())
            assertTrue(canModifyRoles())
            assertTrue(canModifyBusinessIdentity())
            assertTrue(canModifyGroups())
            assertTrue(canModifyMembership())
        }
        mockMembership(roles = setOf(MemberRole())).apply {
            assertFalse(canActivateMembership())
            assertFalse(canSuspendMembership())
            assertFalse(canRevokeMembership())
            assertFalse(canModifyRoles())
            assertFalse(canModifyBusinessIdentity())
            assertFalse(canModifyGroups())
            assertFalse(canModifyMembership())
        }

        val adminCustomRole = BNRole("admin-role", setOf(AdminPermission.CAN_ACTIVATE_MEMBERSHIP, mock<BNPermission>()))
        mockMembership(roles = setOf(adminCustomRole)).apply {
            assertTrue(canActivateMembership())
            assertFalse(canSuspendMembership())
            assertFalse(canRevokeMembership())
            assertFalse(canModifyRoles())
            assertFalse(canModifyBusinessIdentity())
            assertFalse(canModifyGroups())
            assertTrue(canModifyMembership())
        }

        val memberCustomRole = BNRole("member-role", setOf(mock()))
        mockMembership(roles = setOf(memberCustomRole)).apply {
            assertFalse(canActivateMembership())
            assertFalse(canSuspendMembership())
            assertFalse(canRevokeMembership())
            assertFalse(canModifyRoles())
            assertFalse(canModifyBusinessIdentity())
            assertFalse(canModifyGroups())
            assertFalse(canModifyMembership())
        }
    }
}