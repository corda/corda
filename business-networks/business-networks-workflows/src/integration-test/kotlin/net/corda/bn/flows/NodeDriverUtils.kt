package net.corda.bn.flows

import net.corda.bn.states.BNIdentity
import net.corda.bn.states.BNRole
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.InProcess
import net.corda.testing.driver.NodeHandle

/** Starts multiple nodes simultaneously, then waits for them all to be ready. **/
fun DriverDSL.startNodes(identities: List<Party>) = identities.map {
    startNode(providedName = it.name)
}.map {
    it.getOrThrow() as InProcess
}

/** Returns identity of [NodeHandle] calling the method. **/
fun NodeHandle.identity() = nodeInfo.legalIdentities.single()

/** Helper method to call [CreateBusinessNetworkFlow] from [NodeHandle]. **/
fun NodeHandle.createBusinessNetwork(
        networkId: UniqueIdentifier = UniqueIdentifier(),
        businessIdentity: BNIdentity? = null,
        groupId: UniqueIdentifier = UniqueIdentifier(),
        groupName: String? = null,
        notary: Party? = null
): MembershipState {
    val stx = rpc.startFlow(::CreateBusinessNetworkFlow, networkId, businessIdentity, groupId, groupName, notary)
            .returnValue.getOrThrow()
    return stx.tx.outputStates.single() as MembershipState
}

/** Helper method to call [RequestMembershipFlow] from [NodeHandle]. **/
fun NodeHandle.requestMembership(
        authorisedParty: Party,
        networkId: String,
        businessIdentity: BNIdentity? = null,
        notary: Party? = null
): MembershipState {
    val stx = rpc.startFlow(::RequestMembershipFlow, authorisedParty, networkId, businessIdentity, notary)
            .returnValue.getOrThrow()
    return stx.tx.outputStates.single() as MembershipState
}

/** Helper method to call [ActivateMembershipFlow] from [NodeHandle]. **/
fun NodeHandle.activateMembership(membershipId: UniqueIdentifier, notary: Party? = null): MembershipState {
    val stx = rpc.startFlow(::ActivateMembershipFlow, membershipId, notary).returnValue.getOrThrow()
    return stx.tx.outputStates.single() as MembershipState
}

/** Helper method to call [SuspendMembershipFlow] from [NodeHandle]. **/
fun NodeHandle.suspendMembership(membershipId: UniqueIdentifier, notary: Party? = null): MembershipState {
    val stx = rpc.startFlow(::SuspendMembershipFlow, membershipId, notary).returnValue.getOrThrow()
    return stx.tx.outputStates.single() as MembershipState
}

/** Helper method to call [RevokeMembershipFlow] from [NodeHandle]. **/
fun NodeHandle.revokeMembership(membershipId: UniqueIdentifier, notary: Party? = null) {
    rpc.startFlow(::RevokeMembershipFlow, membershipId, notary).returnValue.getOrThrow()
}

/** Helper method to call [ModifyRolesFlow] from [NodeHandle]. **/
fun NodeHandle.modifyRoles(membershipId: UniqueIdentifier, roles: Set<BNRole>, notary: Party? = null): MembershipState {
    val stx = rpc.startFlow(::ModifyRolesFlow, membershipId, roles, notary).returnValue.getOrThrow()
    return stx.tx.outputStates.single() as MembershipState
}

/** Helper method to call [ModifyBusinessIdentityFlow] from [NodeHandle]. **/
fun NodeHandle.modifyBusinessIdentity(membershipId: UniqueIdentifier, businessIdentity: BNIdentity, notary: Party? = null): MembershipState {
    val stx = rpc.startFlow(::ModifyBusinessIdentityFlow, membershipId, businessIdentity, notary).returnValue.getOrThrow()
    return stx.tx.outputStates.single() as MembershipState
}

/** Helper method to call [CreateGroupFlow] from [NodeHandle]. **/
fun NodeHandle.createGroup(
        networkId: String,
        groupId: UniqueIdentifier = UniqueIdentifier(),
        groupName: String? = null,
        additionalParticipants: Set<UniqueIdentifier> = emptySet(),
        notary: Party? = null
): GroupState {
    val stx = rpc.startFlow(::CreateGroupFlow, networkId, groupId, groupName, additionalParticipants, notary).returnValue.getOrThrow()
    return stx.tx.outputStates.single() as GroupState
}

/** Helper method to call [ModifyGroupFlow] from [NodeHandle]. **/
fun NodeHandle.modifyGroup(groupId: UniqueIdentifier, name: String? = null, participants: Set<UniqueIdentifier>? = null, notary: Party? = null): GroupState {
    val stx = rpc.startFlow(::ModifyGroupFlow, groupId, name, participants, notary).returnValue.getOrThrow()
    return stx.tx.outputStates.single() as GroupState
}

/** Helper method to call [DeleteGroupFlow] from [NodeHandle]. **/
fun NodeHandle.deleteGroup(groupId: UniqueIdentifier, notary: Party? = null) {
    rpc.startFlow(::DeleteGroupFlow, groupId, notary).returnValue.getOrThrow()
}