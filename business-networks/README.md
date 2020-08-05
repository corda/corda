# Business networks and memberships

The Corda platform extension for creating and managing business networks allows a node operator to define and create a logical network based on a set of common CorDapps as well as a shared
business context. Corda nodes outside of a business network are not aware of its members. The network can be split into subgroups or membership lists which allows for further privacy (members of a group
only know about those in their group).

In a business network, there is at least one *authorised member*. This member has sufficient permissions to execute management operations over the network and its members.

# Create a business network and manage it

BNE provides a set of workflows that allows a user to start a business network, on-board members and assign them to membership lists or groups. The flows can also be used to update the information
in the memberships (update business network identity, modify member roles) and manage them (suspend or revoke members).

## Create a business network

Either from the node shell or from an RPC client, simply run ```CreateBusinessNetworkFlow```. This will self issue a membership with an exhaustive permissions set that will
allow the calling node to manage future operations for the newly created network.

**Flow arguments:**

- ```networkId``` Custom ID to be given to the new Business Network. If not specified, a randomly selected one will be used
- ```businessIdentity``` Optional custom business identity to be given to membership
- ```groupId``` Custom ID to be given to the initial Business Network group. If not specified, randomly selected one will be used
- ```groupName``` Optional name to be given to the Business Network group
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:
```kotlin
val myIdentity: BNIdentity = createBusinessNetworkIdentity() // mock method that creates an instance of a class implementing [BNIdentity]
val groupId = UniqueIdentifier()
val notary = serviceHub.networkMapCache.notaryIdentities.first()

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::CreateBusinessNetworkFlow, "MyBusinessNetwork", myIdentity, groupId, "Group 1", notary)
            .returnValue.getOrThrow()
}
```

## On-board a new member

Joining a business network is a 2 step process. First, the Corda node wishing to join must run the ```RequestMembershipFlow``` either from the node shell or any other RPC client.
As a result of a successful run, a membership is created with a *PENDING* status and all authorised members will be notified of any future operations involving it. Until activated
by an authorised party (a business network operator for instance) the newly generated membership can neither be used nor grant the requesting node any permissions in the business network.

**RequestMembershipFlow arguments**:

- ```authorisedParty``` Identity of authorised member from whom membership activation is requested
- ```networkId``` ID of the Business Network that potential new member wants to join
- ```businessIdentity``` Optional custom business identity to be given to membership
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val myIdentity: BNIdentity = createBusinessNetworkIdentity() // create an instance of a class implementing [BNIdentity]
val networkId = "MyBusinessNetwork"
val bno: Party = ... // get the [Party] object of the Corda node acting as a BNO for the business network represented by [networkId]
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::RequestMembershipFlow, bno, networkId, myIdentity, notary)
            .returnValue.getOrThrow()
}
```

To finalise the on-boarding process, an authorised party needs to run the ```ActivateMembershipFlow```. This will update the targeted membership status from *PENDING* to *ACTIVE* after
signatures are collected from **all** authorised parties in the network. After activation, the activating party needs to follow-up with a group assignment by running the ```ModifyGroupFlow```.

**ActivateMembershipFlow arguments**:

- ```membershipId``` ID of the membership to be activated
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val bnService = serviceHub.cordaService(DatabaseService::class.java)
val networkId = "MyBusinessNetwork"
val newMemberPartyObject = ... // get the [Party] object of the member whose membership is being activated
val membershipId = bnService.getMembership("MyBusinessNetwork", newMemberPartyObject)
val groupName = "Group 1"
val groupId = ... // identifier of the group which the member will be assigned to
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::ActivateMembershipFlow, membershipId, notary)
            .returnValue.getOrThrow()

    // add newly activated member to a membership list
    val newParticipantsList = bnService.getBusinessNetworkGroup(groupId).state.data.participants.map {
        databaseService.getMembership(networkId, it)!!.state.data.linearId
    } + membershipId

    it.proxy.startFlow(::ModifyGroupFlow, groupId, groupName, newParticipantsList, notary)
            .returnValue.getOrThrow()
}
```

## Amend a membership

There are several ways in which a member's information can be updated, not including network operations such as membership suspension or revocation. These attributes which can be amended are:
business network identity, membership list or group, and roles.

To update a member's business identity attribute, one of the authorised network parties needs to run the ```ModifyBusinessIdentityFlow``` which then requires all network members with
sufficient permissions to approve the proposed change.

**ModifyBusinessIdentityFlow arguments**:

- ```membershipId``` ID of the membership to modify business identity
- ```businessIdentity``` Optional custom business identity to be given to membership
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val bnService = serviceHub.cordaService(DatabaseService::class.java)
val networkId = "MyBusinessNetwork"
val partyToBeUpdated = ... // get the [Party] object of the member being updated
val membership = bnService.getMembership(networkId, partyToBeUpdated)
val updatedIdentity: BNIdentity = updateBusinessIdentity(membership.state.data.identity) // mock method that updates the business identity in some meaningful way
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::ModifyBusinessIdentityFlow, membership.state.data.linearId, updatedIdentity, notary)
            .returnValue.getOrThrow()
}
```

Updating a member's roles and permissions in the business network is done in a similar fashion by using the ```ModifyRolesFlow```. Depending on the proposed changes, the updated member may become
an authorised member. If that happens, an important thing to note is that this enhancement will have to be preceded by an execution of the ```ModifyGroupsFlow``` to add the member to all membership
lists it will have administrative powers over.

**ModifyRolesFlow arguments**:

- ```membershipId``` ID of the membership to assign roles
- ```roles``` Set of roles to be assigned to membership
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val roles = setOf(BNORole()) // assign full permissions to member
val bnService = serviceHub.cordaService(DatabaseService::class.java)
val networkId = "MyBusinessNetwork"
val partyToBeUpdated = ... // get the [Party] object of the member being updated
val membershipId = bnService.getMembership(networkId, partyToBeUpdated).state.data.linearId
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::ModifyRolesFlow, membershipId, roles, notary)
            .returnValue.getOrThrow()
}
```

To manage the membership lists or groups, one of the authorised members of the network can use ```CreateGroupFlow```, ````DeleteGroupFlow``` and ```ModifyGroupFlow```.

**CreateGroupFlow arguments**:

- ```networkId``` ID of the Business Network that Business Network Group will relate to
- ```groupId``` Custom ID to be given to the issued Business Network Group. If not specified, randomly selected one will be used
- ```groupName``` Optional name to be given to the issued Business Network Group
- ```additionalParticipants``` Set of participants to be added to issued Business Network Group alongside initiator's identity
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

**Example**:

```kotlin
val notary = serviceHub.networkMapCache.notaryIdentities.first())
val myNetworkId = "MyBusinessNetwork"
val myGroupId = UniqueIdentifier()
val groupName = "Group 1"
CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::CreateGroupFlow, myNetworkId, myGroupId, groupName, emptySet(), notary)
            .returnValue.getOrThrow()
}
```

**DeleteGroupFlow arguments**:

- ```groupId``` ID of group to be deleted
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

The ```ModifyGroupFlow``` can update the name of a group and/or its list of members.

**ModifyGroupFlow arguments**:

- ```groupId``` ID of group to be modified
- ```name``` New name of modified group
- ```participants``` New participants of modified group
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val bnService = serviceHub.cordaService(DatabaseService::class.java)
val bnGroupId = ... // get the identifier of the group being updated
val bnGroupName = bnService.getBusinessNetworkGroup(bnGroupId).state.data.name
val participantsList = bnService.getBusinessNetworkGroup(bnGroupId).state.data.participants
val newParticipantsList = removeMember(someMember, participantsList) // mock method that removes a member from the group
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::ModifyGroupFlow, bnGroupId, bnGroupName, newParticipantsList, notary)
            .returnValue.getOrThrow()
}
```

## Suspend or revoke a membership

Temporarily suspending a member or completely removing it from the business network is done using ```SuspendMembershipFlow``` and ```RevokeMembershipFlow```. They both use the same exactly arguments:

- ```membershipId``` ID of the membership to be suspended/revoked
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

Suspending a member will result in a membership status change to ```SUSPENDED``` and still allow said member to be in the business network. Revocation means that the membership is marked as historic/spent
and and a new one will have to be requested and activated in order for the member to re-join the network.

*Example*:

```kotlin
val notary = serviceHub.networkMapCache.notaryIdentities.first())
val memberToBeSuspended = ... // get the linear ID of the membership state associated with the Party which is being suspended from the network
val memberToBeRevoked = ... // get the linear ID of the membership state associated with the Party which is being removed from the network
// Revocation
CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::RevokeMembershipFlow, memberToBeRevoked, notary)
            .returnValue.getOrThrow()
}

// Suspension
CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::RevokeMembershipFlow, memberToBeSuspended, notary)
            .returnValue.getOrThrow()
}
```