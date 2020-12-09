package net.corda.node.services.network

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.node.EndpointInfo
import net.corda.core.node.MemberInfo
import net.corda.core.node.MemberRole
import net.corda.core.node.MemberStatus
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersistentMembershipGroupCacheTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val manager = TestIdentity(ALICE_NAME, 70).party
    private val alice = TestIdentity(BOB_NAME, 80).party
    private val bob = TestIdentity(CHARLIE_NAME, 90).party
    private val charlie = TestIdentity(CHARLIE_NAME, 100).party

    private val managerInfo = createMemberInfo(manager, 1234, MemberRole.MANAGER)
    private val aliceInfo = createMemberInfo(alice, 1235)
    private val bobInfo = createMemberInfo(bob, 1236)

    private val database = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })
    private val membershipGroupCache = PersistentMembershipGroupCache(database, managerInfo)

    @Before
    internal fun setUp() {
        membershipGroupCache.addOrUpdateMembers(listOf(aliceInfo, bobInfo))
    }

    @After
    internal fun tearDown() {
        database.close()
    }

    private fun createMemberInfo(party: Party, port: Int, role: MemberRole = MemberRole.NODE): MemberInfo {
        return MemberInfo(
                memberId = party.owningKey.toStringShort(),
                party = party,
                keys = listOf(party.owningKey),
                endpoints = listOf(EndpointInfo("https://localhost:$port", party.name.x500Principal, 1)),
                status = MemberStatus.ACTIVE,
                softwareVersion = "dummy_version",
                platformVersion = 1,
                role = role,
                properties = mapOf()
        )
    }

    @Test(timeout = 300_000)
    fun `insert member into database`() {
        assertEquals(aliceInfo, membershipGroupCache.getMemberInfo(alice))
        assertEquals(bobInfo, membershipGroupCache.getMemberInfo(bob))
        assertNull(membershipGroupCache.getMemberInfo(charlie))
    }

    @Test(timeout = 300_000)
    fun `update member info`() {
        val properties = mapOf("username" to "dummy")
        membershipGroupCache.addOrUpdateMember(aliceInfo.copy(status = MemberStatus.SUSPENDED, properties = properties))
        val updatedMember = membershipGroupCache.getMemberInfo(alice)
        assertEquals(MemberStatus.SUSPENDED, updatedMember?.status)
        assertEquals(properties, updatedMember?.properties)
    }

    @Test(timeout = 300_000)
    fun `remove member info`() {
        assertEquals(aliceInfo, membershipGroupCache.getMemberInfo(alice))
        membershipGroupCache.removeMember(aliceInfo)
        assertNull(membershipGroupCache.getMemberInfo(alice))
        assertEquals(bobInfo, membershipGroupCache.getMemberInfo(bob))
    }

    @Test(timeout = 300_000)
    fun `get member info by id`() {
        assertEquals(aliceInfo, membershipGroupCache.getMemberInfo(alice.owningKey.toStringShort()))
        assertEquals(bobInfo, membershipGroupCache.getMemberInfo(bob.owningKey.toStringShort()))
        assertNull(membershipGroupCache.getMemberInfo(charlie.owningKey.toStringShort()))
    }

    @Test(timeout = 300_000)
    fun `get all members`() {
        assertThat(membershipGroupCache.allMembers).containsExactlyInAnyOrder(aliceInfo, bobInfo)
    }
}
