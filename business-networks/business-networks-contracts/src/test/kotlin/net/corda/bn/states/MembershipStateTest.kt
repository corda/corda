package net.corda.bn.states

import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MembershipStateTest {

    private fun mockMembership(status: MembershipStatus = MembershipStatus.PENDING, roles: Set<BNRole> = emptySet()) = MembershipState(
            identity = mock(),
            networkId = "",
            status = status,
            roles = roles,
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
