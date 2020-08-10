package net.corda.bn.flows

import net.corda.bn.states.BNORole
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseServiceTest : MembershipManagementFlowTest(numberOfAuthorisedMembers = 1, numberOfRegularMembers = 1) {

    private fun DatabaseService.getAllMemberships(networkId: String) = getAllMembershipsWithStatus(
            networkId,
            MembershipStatus.PENDING, MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED
    ).map {
        it.state.data.linearId
    }

    @Test(timeout = 300_000)
    fun `business network exists method should work`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMemberService = authorisedMember.services.cordaService(DatabaseService::class.java)
        val regularMemberService = regularMember.services.cordaService(DatabaseService::class.java)

        val invalidNetworkId = "invalid-network-id"
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertFalse(service.businessNetworkExists(invalidNetworkId)) }

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        assertTrue(authorisedMemberService.businessNetworkExists(networkId))
        assertFalse(regularMemberService.businessNetworkExists(networkId))

        val membership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.businessNetworkExists(networkId)) }

        runSuspendMembershipFlow(authorisedMember, membership.linearId)
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.businessNetworkExists(networkId)) }

        runRevokeMembershipFlow(authorisedMember, membership.linearId)
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.businessNetworkExists(networkId)) }
    }

    @Test(timeout = 300_000)
    fun `get membership methods should work`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMemberService = authorisedMember.services.cordaService(DatabaseService::class.java)
        val regularMemberService = regularMember.services.cordaService(DatabaseService::class.java)

        val invalidMembershipId = UniqueIdentifier()
        val invalidNetworkId = "invalid-network-id"
        val invalidIdentity = TestIdentity(CordaX500Name.parse("O=InvalidOrganisation,L=New York,C=US")).party
        listOf(authorisedMemberService, regularMemberService).forEach { service ->
            assertNull(service.getMembership(invalidMembershipId))
            assertNull(service.getMembership(invalidNetworkId, invalidIdentity))
        }

        val authorisedMembership = runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState
        authorisedMembership.apply {
            assertEquals(linearId, authorisedMemberService.getMembership(linearId)?.state?.data?.linearId)
            assertEquals(linearId, authorisedMemberService.getMembership(networkId, identity.cordaIdentity)?.state?.data?.linearId)
            assertNull(regularMemberService.getMembership(linearId))
            assertNull(regularMemberService.getMembership(networkId, identity.cordaIdentity))
        }

        val regularMembership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, authorisedMembership.networkId).tx.outputStates.single() as MembershipState
        listOf(authorisedMembership, regularMembership).forEach { membership ->
            membership.apply {
                assertEquals(linearId, authorisedMemberService.getMembership(linearId)?.state?.data?.linearId)
                assertEquals(linearId, authorisedMemberService.getMembership(networkId, identity.cordaIdentity)?.state?.data?.linearId)
                assertEquals(linearId, regularMemberService.getMembership(linearId)?.state?.data?.linearId)
                assertEquals(linearId, regularMemberService.getMembership(networkId, identity.cordaIdentity)?.state?.data?.linearId)
            }
        }

        val suspendedMembership = runSuspendMembershipFlow(authorisedMember, regularMembership.linearId).tx.outputStates.single() as MembershipState
        listOf(authorisedMembership, suspendedMembership).forEach { membership ->
            membership.apply {
                assertEquals(linearId, authorisedMemberService.getMembership(linearId)?.state?.data?.linearId)
                assertEquals(linearId, authorisedMemberService.getMembership(networkId, identity.cordaIdentity)?.state?.data?.linearId)
                assertEquals(linearId, regularMemberService.getMembership(linearId)?.state?.data?.linearId)
                assertEquals(linearId, regularMemberService.getMembership(networkId, identity.cordaIdentity)?.state?.data?.linearId)
            }
        }

        runRevokeMembershipFlow(authorisedMember, suspendedMembership.linearId)
        authorisedMembership.apply {
            assertEquals(linearId, authorisedMemberService.getMembership(linearId)?.state?.data?.linearId)
            assertEquals(linearId, authorisedMemberService.getMembership(networkId, identity.cordaIdentity)?.state?.data?.linearId)
            assertNull(regularMemberService.getMembership(linearId))
            assertNull(regularMemberService.getMembership(networkId, identity.cordaIdentity))
        }
        suspendedMembership.apply {
            assertNull(authorisedMemberService.getMembership(linearId))
            assertNull(authorisedMemberService.getMembership(networkId, identity.cordaIdentity))
            assertNull(regularMemberService.getMembership(linearId))
            assertNull(regularMemberService.getMembership(networkId, identity.cordaIdentity))
        }
    }

    @Test(timeout = 300_000)
    fun `get all memberships with status method should work`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMemberService = authorisedMember.services.cordaService(DatabaseService::class.java)
        val regularMemberService = regularMember.services.cordaService(DatabaseService::class.java)

        val invalidNetworkId = "invalid-network-id"
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.getAllMemberships(invalidNetworkId).isEmpty()) }

        val authorisedMembership = runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState
        assertEquals(setOf(authorisedMembership.linearId), authorisedMemberService.getAllMemberships(authorisedMembership.networkId).toSet())
        assertTrue(regularMemberService.getAllMemberships(authorisedMembership.networkId).isEmpty())

        val regularMembership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, authorisedMembership.networkId).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service ->
            assertEquals(setOf(authorisedMembership.linearId, regularMembership.linearId), service.getAllMemberships(authorisedMembership.networkId).toSet())
        }

        val suspendedMembership = runSuspendMembershipFlow(authorisedMember, regularMembership.linearId).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service ->
            assertEquals(setOf(authorisedMembership.linearId, suspendedMembership.linearId), service.getAllMemberships(authorisedMembership.networkId).toSet())
            assertEquals(setOf(authorisedMembership.linearId), service.getAllMembershipsWithStatus(authorisedMembership.networkId, MembershipStatus.ACTIVE).map { it.state.data.linearId }.toSet())
            assertEquals(setOf(suspendedMembership.linearId), service.getAllMembershipsWithStatus(authorisedMembership.networkId, MembershipStatus.SUSPENDED).map { it.state.data.linearId }.toSet())
            assertTrue(service.getAllMembershipsWithStatus(authorisedMembership.networkId, MembershipStatus.PENDING).isEmpty())
        }

        runRevokeMembershipFlow(authorisedMember, suspendedMembership.linearId)
        assertEquals(setOf(authorisedMembership.linearId), authorisedMemberService.getAllMemberships(authorisedMembership.networkId).toSet())
        assertTrue(regularMemberService.getAllMemberships(authorisedMembership.networkId).isEmpty())
    }

    @Test(timeout = 300_000)
    fun `get members authorised to modify membership method should work`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMemberService = authorisedMember.services.cordaService(DatabaseService::class.java)
        val regularMemberService = regularMember.services.cordaService(DatabaseService::class.java)

        val invalidNetworkId = "invalid-network-id"
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.getMembersAuthorisedToModifyMembership(invalidNetworkId).isEmpty()) }

        val authorisedMembership = runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState
        assertEquals(setOf(authorisedMembership.linearId), authorisedMemberService.getMembersAuthorisedToModifyMembership(authorisedMembership.networkId).map { it.state.data.linearId }.toSet())
        assertTrue(regularMemberService.getMembersAuthorisedToModifyMembership(authorisedMembership.networkId).isEmpty())

        val regularMembership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, authorisedMembership.networkId).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service ->
            assertEquals(setOf(authorisedMembership.linearId), service.getMembersAuthorisedToModifyMembership(authorisedMembership.networkId).map { it.state.data.linearId }.toSet())
        }

        val bnoMembership = runModifyRolesFlow(authorisedMember, regularMembership.linearId, setOf(BNORole())).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service ->
            assertEquals(setOf(authorisedMembership.linearId, bnoMembership.linearId), service.getMembersAuthorisedToModifyMembership(authorisedMembership.networkId).map { it.state.data.linearId }.toSet())
        }

        val suspendedMembership = runSuspendMembershipFlow(authorisedMember, bnoMembership.linearId).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service ->
            assertEquals(setOf(authorisedMembership.linearId, suspendedMembership.linearId), service.getMembersAuthorisedToModifyMembership(authorisedMembership.networkId).map { it.state.data.linearId }.toSet())
        }

        runRevokeMembershipFlow(authorisedMember, suspendedMembership.linearId)
        assertEquals(setOf(authorisedMembership.linearId), authorisedMemberService.getMembersAuthorisedToModifyMembership(authorisedMembership.networkId).map { it.state.data.linearId }.toSet())
        assertTrue(regularMemberService.getMembersAuthorisedToModifyMembership(authorisedMembership.networkId).isEmpty())
    }

    @Test(timeout = 300_000)
    fun `business network groups exists method should work`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMemberService = authorisedMember.services.cordaService(DatabaseService::class.java)
        val regularMemberService = regularMember.services.cordaService(DatabaseService::class.java)

        val invalidGroupId = UniqueIdentifier()
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertFalse(service.businessNetworkGroupExists(invalidGroupId)) }

        val groupId = UniqueIdentifier()
        val networkId = (runCreateBusinessNetworkFlow(authorisedMember, groupId = groupId).tx.outputStates.single() as MembershipState).networkId
        assertTrue(authorisedMemberService.businessNetworkGroupExists(groupId))
        assertFalse(regularMemberService.businessNetworkGroupExists(groupId))

        val membership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.businessNetworkGroupExists(groupId)) }

        runSuspendMembershipFlow(authorisedMember, membership.linearId)
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.businessNetworkGroupExists(groupId)) }

        runRevokeMembershipFlow(authorisedMember, membership.linearId)
        assertTrue(authorisedMemberService.businessNetworkGroupExists(groupId))
        assertFalse(regularMemberService.businessNetworkGroupExists(groupId))
    }

    @Test(timeout = 300_000)
    fun `get business network group method should work`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMemberService = authorisedMember.services.cordaService(DatabaseService::class.java)
        val regularMemberService = regularMember.services.cordaService(DatabaseService::class.java)

        val invalidGroupId = UniqueIdentifier()
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertNull(service.getBusinessNetworkGroup(invalidGroupId)) }

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        val groupId = authorisedMemberService.getAllBusinessNetworkGroups(networkId).single().state.data.linearId
        assertEquals(setOf(authorisedMember.identity()), authorisedMemberService.getBusinessNetworkGroup(groupId)?.state?.data?.participants?.toSet())
        assertNull(regularMemberService.getBusinessNetworkGroup(groupId))

        val membership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service ->
            assertEquals(setOf(authorisedMember.identity(), regularMember.identity()), service.getBusinessNetworkGroup(groupId)?.state?.data?.participants?.toSet())
        }

        runSuspendMembershipFlow(authorisedMember, membership.linearId).tx.outputStates.single()
        listOf(authorisedMemberService, regularMemberService).forEach { service ->
            assertEquals(setOf(authorisedMember.identity(), regularMember.identity()), service.getBusinessNetworkGroup(groupId)?.state?.data?.participants?.toSet())
        }

        runRevokeMembershipFlow(authorisedMember, membership.linearId)
        assertEquals(setOf(authorisedMember.identity()), authorisedMemberService.getBusinessNetworkGroup(groupId)?.state?.data?.participants?.toSet())
        assertNull(regularMemberService.getBusinessNetworkGroup(groupId))
    }

    @Test(timeout = 300_000)
    fun `get all business network groups method should work`() {
        val authorisedMember = authorisedMembers.first()
        val regularMember = regularMembers.first()

        val authorisedMemberService = authorisedMember.services.cordaService(DatabaseService::class.java)
        val regularMemberService = regularMember.services.cordaService(DatabaseService::class.java)

        val invalidNetworkId = "invalid-network-id"
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.getAllBusinessNetworkGroups(invalidNetworkId).isEmpty()) }

        val networkId = (runCreateBusinessNetworkFlow(authorisedMember).tx.outputStates.single() as MembershipState).networkId
        assertTrue(authorisedMemberService.getAllBusinessNetworkGroups(networkId).isNotEmpty())
        assertTrue(regularMemberService.getAllBusinessNetworkGroups(networkId).isEmpty())

        val membership = runRequestAndActivateMembershipFlows(regularMember, authorisedMember, networkId).tx.outputStates.single() as MembershipState
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.getAllBusinessNetworkGroups(networkId).isNotEmpty()) }

        runSuspendMembershipFlow(authorisedMember, membership.linearId).tx.outputStates.single()
        listOf(authorisedMemberService, regularMemberService).forEach { service -> assertTrue(service.getAllBusinessNetworkGroups(networkId).isNotEmpty()) }

        runRevokeMembershipFlow(authorisedMember, membership.linearId)
        assertTrue(authorisedMemberService.getAllBusinessNetworkGroups(networkId).isNotEmpty())
        assertTrue(regularMemberService.getAllBusinessNetworkGroups(networkId).isEmpty())
    }
}