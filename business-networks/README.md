# Business networks and memberships

The Corda platform extension for creating and managing business networks allows a node operator to define and create a logical network based on a set of common CorDApps as well as a shared
business context.


# Install the BNE (!!!name might change!!!) CorDApp

WIP, need to find a repo first.

# Create a business network and manage it

BNE provides a set of workflows that allows a user to start a business network, on-board members and assign them to membership lists or groups. The flows can also be used to update the information
in the memberships (update business network identity, modify member roles) and manage them (suspend or revoke members).

## Create a business network

Either from the node shell or from an RPC client, simply run ```CreateBusinessNetworkFlow```. This will self issue a membership with an exhaustive permissions set that will
allow the calling node to manage future operations for the newly created network.

**Flow arguments:**

- ```networkId``` Custom ID to be given to the new Business Network. If not specified, a randomly selected one will be used
- ```businessIdentity``` Custom business identity to be given to membership
- ```groupId``` Custom ID to be given to the initial Business Network group. If not specified, randomly selected one will be used
- ```groupName``` Optional name to be given to the Business Network group
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:
```kotlin
val myIdentity: BNIdentity = createBusinessNetworkIdentity() // create an instance of a class implementing [BNIdentity]
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::CreateBusinessNetworkFlow, "MyBusinessNetwork", myIdentity, "1", "X-men-and-women", notary)
            .returnValue.getOrThrow()
}
```

## On-board a new member

Joining a business network is a 2 step process. First, the Corda node wishing to join must run the ```RequestMembershipFlow``` either from the node shell or any other RPC client.
As a result of a successful run, a membership is created with a *PENDING* status. Until activated by an authorised party (a business network operator for instance) the newly generated
membership can neither be used nor grant the requesting node any permissions in the business network.

**RequestMembershipFlow arguments**:

- ```authorisedParty``` Identity of authorised member from whom membership activation is requested
- ```networkId``` ID of the Business Network that potential new member wants to join
- ```businessIdentity``` Custom business identity to be given to membership
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val myIdentity: BNIdentity = createBusinessNetworkIdentity() // create an instance of a class implementing [BNIdentity]
val bno: Party = getParty("BestBNO")
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::RequestMembershipFlow, bno, "MyBusinessNetwork", myIdentity, notary)
            .returnValue.getOrThrow()
}
```

To finalise the on-boarding process, an authorised party needs to run the ```ActivateMembershipFlow```. This will update the targeted membership status from *PENDING* to *ACTIVE*.
Activation requires approval from **all** authorised parties in the network.

**ActivateMembershipFlow arguments**:

- ```membershipId``` ID of the membership to be activated
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::ActivateMembershipFlow, 123456, notary)
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
- ```businessIdentity``` Custom business identity to be given to membership
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val bnService = serviceHub.cordaService(DatabaseService::class.java)
val updatedIdentity: BNIdentity = updateBusinessIdentity(bnService.getMembership("MyBusinessNetwork", partyToBeUpdated))
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::ModifyBusinessIdentityFlow, 123456, updatedIdentity, notary)
            .returnValue.getOrThrow()
}
```

Updating a member's roles and permissions in the business network is done in a similar fashion by using the ```ModifyRolesFlow```. Depending on the proposed changes, the updated member may become
an authorised member, in which case they will be added to all membership lists in order to be notified of any future business network changes.

**ModifyRolesFlow arguments**:

- ```membershipId``` ID of the membership to assign roles
- ```roles``` Set of roles to be assigned to membership
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val roles = setOf(BNORole) // assign full permissions to member
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::ModifyRolesFlow, 123456, roles, notary)
            .returnValue.getOrThrow()
}
```

To manage the membership lists or groups, one of the authorised members of the network can use ```DeleteGroupFlow``` or ```ModifyGroupFlow```.

**DeleteGroupFlow arguments**:

- ```groupId``` ID of group to be deleted
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

The ```ModifyGroupFlow``` can update the name of a group and/or its list of members.

***ModifyGroupFlow arguments**:

- ```groupId``` ID of group to be modified
- ```name``` New name of modified group
- ```participants``` New participants of modified group
- ```notary``` Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used

*Example*:

```kotlin
val bnService = serviceHub.cordaService(DatabaseService::class.java)
val participantsList = getBusinessNetworkGroup("1").state.data.participants
val newParticipantsList = removeMember(getParty("Wolverine"), participantsList)
val notary = serviceHub.networkMapCache.notaryIdentities.first())

CordaRPCClient(rpcAddress).start(user.userName, user.password).use {
    it.proxy.startFlow(::ModifyGroupFlow, "1"", "New Mutants", newParticipantsList, notary)
            .returnValue.getOrThrow()
}
```

## Suspend or revoke a membership



## Integrate with CorDApps

!!! WIP This is where going over how to implement a cordapp that works with this should be done. Waiting on Ante's sample cordapps to get merged. !!!