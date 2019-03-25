# Migration from the hash constraint to the Signature constraint


## Background

Corda pre-V4 only supports HashConstraints and the WhitelistedByZoneConstraint.
The default constraint, if no entry was added to the network parameters is the hash constraint.
Thus, it's very likely that most first states were created with the Hash constraint.

When changes will be required to the contract, the only alternative is the explicit upgrade, which creates a new contract, but inherits the HashConstraint (with the hash of the new jar this time). 

**The current implementation of the explicit upgrade does not support changing the constraint.**

It's very unlikely that these first deployments actually wanted a non-upgradeable version.

This design doc is presenting a smooth migration path from the hash constraint to the signature constraint. 


## Goals

CorDapps that were released (states created) with the hash constraint should be able to transition to the signature constraint if the original developer decides to do that.

A malicious party should not be able to attack this feature, by "taking ownership" of the original code.


## Non-Goals

Migration from the whitelist constraint was already implemented. so will not be addressed. (The cordapp developer or owner just needs to sign the jar and whitelist the signed jar.)

Also versioning is being addressed in different design docs.


## Design details

### Requirements

To migrate without disruption from the hash constraint, the jar that is attached to a spending transaction needs to satisfy both the hash constraint of the input state, as well as the signature constraint of the output state.

Also, it needs to reassure future transaction verifiers - when doing transaction resolution - that this was a legitimate transition, and not a malicious attempt to replace the contract logic.


### Process

To achieve the first part, we can create this convention:

- Developer signs the original jar (that was used with the hash constraint).
- Nodes install it, thus whitelisting it.
- The HashConstraint.verify method will be modified to verify the hash with and without signatures.
- The nodes create normal transactions that spend an input state with the hashConstraint and output states with the signature constraint. No special spend-to-self transactions should be required.
- This transaction would validate correctly as both constraints will pass - the unsigned hash matches, and the signatures are there.
- This logic needs to be added to the constraint propagation transition matrix. This could be only enabled for states created pre-v4, when there was no alternative.
    

For the second part:
    
- The developer needs to claim the package (See package ownership). This will give confidence to future verifiers that it was the actual developer that continues to be the owner of that jar.  
    

To summarise, if a CorDapp developer wishes to migrate to the code it controls to the signature constraint for better flexibility:

1. Claim the package. 
2. Sign the jar and distribute it.
3. In time all states will naturally transition to the signature constraint.
4. Release new version as per the signature constraint. 


A normal node would just download the signed jar using the normal process for that, and the platform will do the rest.


### Caveats 

#### Someone really wants to issue states with the HashConstraint, and ensure that can never change. 

 - As mentioned above the transaction builder could only automatically transition states created pre-v4.  
 
 - If this is the original developer of the cordapp, then they can just hardcode the check in the contract that the constraint must be the HashConstraint. 
 
 - It is actually a third party that uses a contract it doesn't own, but wants to ensure that it's only that code that is used. 
 This should not be allowed, as it would clash with states created without this constraint (that might have higher versions), and create incompatible states.  
 The option in this case is to force such parties to actually create a new contract (maybe subclass the version they want), own it, and hardcode the check as above.


####  Some nodes haven't upgraded all their states by the time a new release is already being used on the network.

 - A transaction mixing an original HashConstraint state, and a v2 Signature constraint state will not pass. The only way out is to strongly "encourage" nodes to upgrade before the new release.
  
The problem is that, in a transaction, the attachment needs to pass the constraint of all states.

If the rightful owner took over the contract of states originally released with the HashConstraint, and started releasing new versions then the following might happen:

- NodeA did not migrate all his states to the Signature Constraint.
- NodeB did, and already has states created with version 2.
- If they decide to trade, NodeA will add his HashConstraint state, and NodeB will add his version2 SignatureConstraint state to a new transaction.
This is an impossible transaction. Because if you select version1 of the contract you violate the non-downgrade rule. If you select version2 , you violate the initial HashConstraint.


Note:  If we consider this to be a real problem, then we can implement a new NoOp Transaction type similar to the Notary change or the contract upgrade. The security implications need to be considered before such work is started.
Nodes could use this type of transaction to change the constraint of existing states without the need to transact. 


### Implementation details
 
- Create a function to return the hash of a signed jar after it stripped the signatures. 
- Change the HashConstraint to check against any of these 2 hashes.
- Change the transaction builder logic to automatically transition constraints that are signed, owned, etc..
- Change the constraint propagation transition logic to allow this.



## Alternatives considered


###  Migrating from the HashConstraint to the SignatureConstraint via the WhitelistConstraint. 

We already have a strategy to migrate from the WhitelistConstraint to the Signature contraint:

- Original developer (owner) signs the last version of the jar, and whitelists the signed version. 
- The platform allows transitioning to the SignatureConstraint as long as all the signers of the jar are in the SignatureConstraint.  


We could attempt to extend this strategy with a HashConstraint -> WhitelistConstraint path.

#### The process would be:

- Original developer of the contract that used the hashConstraint will make a whitelist contract request, and provide both the original jar and the original jar but signed.
- The zone operator needs to make sure that this is the original developer who claims ownership of that corDapp. 

##### Option 1: Skip the WhitelistConstraint when spending states. (InputState = HashConstraint, OutputState = SignatureConstraint)

- This is not possible as one of the 2 constraints will fail.
- Special constraint logic is needed which is risky.


##### Option 2: Go through the WhitelistConstraint when spending states

- When a state is spent, the transaction builder sees there is a whitelist constraint, and selects the first entry.
- The transition matrix will allow the transition from hash to Whitelist.
- Next time the state is spent, it will transition from the Whitelist constraint to the signature constraint.  


##### Advantage:

- The tricky step of removing the signature from a jar to calculate the hash is no longer required.


##### Disadvantage:

- The transition will happen in 2 steps, which will add another layer of surprise and of potential problems. 
- The No-Op transaction will become mandatory for this, along with all the complexity it brings (all participants signing). It will need to be run twice. 
- An unnecessary whitelist entry is added. If that developer also decides to claim the package (as probably most will do in the beginning), it will grow the network parameters and increase the workload on the Zone Operator.
- We create an unintended migration path from the HashConstraint to the WhitelistConstraint.
