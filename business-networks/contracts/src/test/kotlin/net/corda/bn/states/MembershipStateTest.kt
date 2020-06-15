package net.corda.bn.states

import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MembershipStateTest {

    private fun mockMembership(status: MembershipStatus) = MembershipState(
            identity = mock(),
            networkId = "",
            status = status,
            participants = mock()
    )

    @Test(timeout = 300_000)
    fun `membership state status helper methods should work`() {
        mockMembership(MembershipStatus.PENDING).apply {
            assertTrue(isPending())
            assertFalse(isActive())
            assertFalse(isSuspended())
        }
        mockMembership(MembershipStatus.ACTIVE).apply {
            assertFalse(isPending())
            assertTrue(isActive())
            assertFalse(isSuspended())
        }
        mockMembership(MembershipStatus.SUSPENDED).apply {
            assertFalse(isPending())
            assertFalse(isActive())
            assertTrue(isSuspended())
        }
    }
}