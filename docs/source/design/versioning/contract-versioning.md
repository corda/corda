# Contract versioning and ensuring data integrity


## Terminology used in this document:

- ContractJAR = The code that contains the: State, Command, Contract and (optional) custom persistent Schema. This code is used to verify transactions and is stored on the ledger as an Attachment. (TODO: Find a better name.)
- FlowsJAR = Code that contains the flows and services. This is installed on the node and exposes endpoints. (TODO: Find a better name.)
- CorDapp = Distributed applications that run on the Corda platform (https://docs.corda.net/cordapp-overview.html). This term does not mean anything in this document, because it is including both of the above!
- Attachment = A file that is stored on the "ledger" and is referenced by it's hash. In this document it is usually the ContractJAR.
- Contract = The class that contains the verification logic. (lives in the ContractJar)
- State schema or State = the fields that compose the ContractState class.


## Background:

This document addresses "Corda as a platform for applications" concerns. 

These applications that run on Corda - CorDapps - as opposed to other blockchains are allowed to be updated to fix bugs and address new requirements.

Corda also allows a lot of flexibility so CorDapps can depend on other CorDapps, which have different release cycles, and participants on the network can have any combination installed.

This document is focused mainly on the "ContractJar" part of the CorDapps, as this is the Smart contract that lives on the ledger.

Starting with version 3, Corda has introduced the WhitelistedByZone Contract Constraint, which is the first constraint that allows the contract and contract state type to evolve.
In version 4 we will introduce the decentralized Signature Constraint, which is also an upgradable (allows evolving) constraint.
This introduces a set of new problems that were not present when the Hash Constraint was the only alternative. (The Hash Constraint is non-upgradeable, as it pins the jar version to the hardcoded hash. It can only be upgraded via the "explicit" mechanism.)

E.g.:
Developer MegaCorp develops a token contract: `com.megacorp.tokens.MegaToken`.
As various issues are discovered, and requirements change over time, MegaCorp constantly releases new versions and distributes them to node operators, who can use these new versions for new transactions.
These versions could in theory either change the verification logic, change the meaning of various state fields, or even add/remove/rename fields.

Also, this means that at any point in time, different nodes may have different versions of MegaToken installed. (and the associated MegaFlows that build transactions using this contract). But these nodes still need to communicate.

Also in the vault of different nodes, there are states created by transactions with various versions of the token contract.

Corda is designed such that the flow that builds the transaction (on its executing node) will have to choose the contract version that will be used to create the output states and also verify the current transaction. 

But because input states are actually output states that are serialised with the previous transaction (built using a potentially different version of the contract), this means that states serialised with a version of the ContractJAR will need to be deserialisable with a different version. 


.. image:: ../../resources/tx-chain.png
   :scale: 25%
   :align: center


## Goals

- States should be correctly deserialized as an input state of a transaction that uses a different release of the contract code. 
After the input states are correctly deserialised they can be correctly verified by the transaction contract.
This is critical for the UTXO model of Corda to function correctly.

- Define a simple process and basic tooling for contract code developers to ensure a consistent release process.   

- Nodes should be prevented to select older buggy contract code for current transactions. 

- Ensure basic mechanism for flows to not miss essential data when communicating with newer flows. 


## Non-Goals

This design is not about:

- Addressing security issues discovered in an older version of a contract (that was used in transactions) without compromising the trust in the ledger. (There are proposals for this and can be extracted in a separate doc) 
- Define the concept of "CorDapp identity" useful when flows or contracts are coded against third-party contracts.
- Evolve states from the HashConstraint or the Whitelist constraint (addressed in a separate design).
- Publishing And Distribution of applications to nodes.
- Contract constraints, package ownership, or any operational concerns.

## Issues considered but postponed for a future version of corda

- How releasing new versions of contract code interacts with the explicit upgrade functionality. 
- How contracts depend on other contracts.
- Node to node communication using flows, and what impact different contract versions have on that. (Flows depending on contracts)
- Versioning of flows and subflows and backwards compatibility concerns.


### Assumptions and trade-offs made for the current version

#### We assume that ContractStates will never change their semantics in a way that would impact other Contracts or Flows that depend on them.
 
E.g.: If various contracts depend on Cash, the assumption is that no new field will be added to Cash that would have an influence over the amount or the owner (the fundamental fields of Cash).
It is always safe to mix new CashStates with older states that depend on it. 

This means that we can simplify the contract-to-contract dependency, also given that the UpgradeableContract is actually a contract that depends on another contract, it can be simplified too.

This is not a very strong definition, so we will have to create more formalised rules in the next releases. 

If any contract breaks this assumption, in Corda 4 there will be no platform support the transition to the new version. The burden of coordinating with all the other cordapp developers and nodes is on the original developer.


#### Flow to Flow communication could be lossy for objects that are not ContractStates or Commands.

Explanation: 
Flows communicate by passing around various objects, and eventually the entire TransactionBuilder.
As flows evolve, these objects might evolve too, even if the sequence stays the same. 
The decision was that the node that sends data would decide (if his version is higher) if this new data is relevant for the other party.

The objects that live on the ledger like ContractStates and Commands that the other party actually has to sign, will not be allowed to lose any data.
 

#### We assume that cordapp developers will correctly understand all implications and handle backwards compatibility themselves.
Basically they will have to realise that any version of a flow can talk to any other version, and code accordingly. 

This get particularly tricky when there are reusable inline subflows involved.


## Design details

### Possible attack under the current implementation

Cordapp developer Megacorp wants to release an update to their cordapp, and add support for accumulating debt on the token (which is a placeholder for something you really want to know about).

- V1: com.megacorp.token.MegaToken(amount: Amount, owner: Party)
- V2: com.megacorp.token.MegaToken(amount: Amount, owner: Party, accumulatedDebt: Amount? = 0)

After they publish the new release, this sort of scenario could happen if we don't have a mechanism to stop it.

1. Tx1: Alice transfers MegaToken to Bob, and selects V1 
2. Tx2: Bob transfers to Chuck, but selects V2. The V1 output state will be deserialised with an accumulatedDebt=0, which is correct.
3. After a while, Chuck accumulates some debt on this token.
4. Txn: Chuck creates a transaction with Dan, but selects V1 as the contract version for this transaction, thus managing to "lose" the `accumulatedDebt`. (V1 does not know about the accumulatedDebt field)



### High level description of the solution

Currently we have the concept of "CorDapp" and, as described in the terminology section, this makes reasoning harder as it is actually composed of 2 parts. 

Contracts and Flows should be able to evolve and be released independently, and have proper names and their own version, even if they share the same gradle multi-module build.

Contract states need to be seen as evolvable objects that can be different from one version to the next.

Corda uses a proprietary serialisation engine based on AMQP, which allows evolution of objects: https://docs.corda.net/serialization-enum-evolution.html.

We can use features already implemented in the serialisation engine and add new features to make sure that data on the ledger is never lost from one transaction to the next. 


### Contract Version 

Contract code should live in it's own gradle module. This is already the way our examples are written.

The Cordapp gradle plugin should be amended to differentiate between a "flows" module and a "contracts" module. 

In the build.gradle file of the contracts module, there should be a `version` property that needs be incremented for each release.

This `version` will be used for the regular release, and be part of the jar name.

Also, when packaging the contract for release, the `version` should be added by the plugin to the manifest file, together with other properties like `target-platform-version`.

When loading the contractJar in the attachment storage, the version should be saved as a column, so it is easily accessible.
 
The new cordapp plugin should also be able to deploy nodes with different versions of the code so that developers can test compatibility.

Ideally the driver should be able to do this too.
 

#### Alternatives considered

The version can be of the `major.minor` format, so that developers can encode if they actually make a breaking change or not.
Given that we assumed that breaking changes are not supported in this version, we can keep it to a simple `major`. 


#### Backwards compatibility

Contracts released before V4 will not have this metadata.

Assuming that the constraints propagated correctly, when verifying a transaction where the constraint:

    - is the HashConstraint the contract can be considered to have `version=1` 
    - is the WhitelistedByZoneConstraint the contract can be considered: `version= Order_of_the_hash_in_the_whitelist` 

Any signed ContractJars should be only considered valid if they have the version metadata. (As signing is a Corda4 feature)



### Protection against losing data on the ledger

The solution we propose is:

- States can only evolve by respecting some predefined rules (see below).
- The serialisation engine will need a new `Strict mode` feature to enforce the evolution rules.
- The `version` metadata of the contract code can be used to make sure that nodes can't spend a state with an older version (downgrade).


#### Contract State evolution

States need to follow the general serialisation rules: https://docs.corda.net/serialization-default-evolution.html

These are the possible evolutions based on these general rules:
 - Adding nullable fields with default values is OK (deserialising old versions would populate the newer fields with the defaults)
 - Adding non-nullable fields with default values is OK but requires extra serialisation annotation
 - Removing fields is permitted, but transactions will fail at the message deserialisation stage if a non-null value is supplied for a removed field. This means that if a nullable field is added, states received from the previous version can be transmitted back to that version, as evolution from the old version to the new version will supply a default value of null for that field, and evolution from the new version back to the old version will discard that null value. If the value is not-null, the data is assumed to have originated from the new version and to be significant for contract validation, and the old version must refuse to handle it.
 - Rename fields NOK ( will be possible in the future when the serialisation engine will support it)
 - Changing type of field NOK (Serialisation engine would most likely fail )
 - Deprecating fields OK (as long as it's not removed)


Given the above reasoning, it means that states only support a subset of the general rules. Basically they can only evolve by adding new fields.
 
Another way to look at this:

    When Contract.verify is written (and compiled), it is in the same project as the current (from it's point of view) version of the State.
    But, at runtime, the contract state it's operating with can actually be a deserialised version of any previous state.
    That's why the current one needs to be a superset of all preceding states, and you can't verify with an older version.
 

The serialisation engine needs to implement the above rules, and needs to run in this `Strict Mode` during transaction verification.
This mode can be implemented as a new `SerializationContext`.

The same serialization evolution `Strict Mode` needs to be enforced any time ContractStates or Commands are serialized. 
This is to ensure that when flows communicate to older versions, the older node will not sign something that he doesn't know.
 

##### Backwards compatibility

The only case when this would break for existing transactions is when the Whitelist Constraint was used and states were evolved not according to the above rules.
Should this rule be applied retroactively, or only for after-v4 transactions?


### Non-downgrade rule

To avoid the possibility of malicious nodes selecting old and buggy contract code when spending newer states, we need to enforce a `Non Downgrade rule`.

Transactions contain an attachment for each contract. The version of the output states is the version of this contract attachment.
(It can be seen as the version of code that instantiated and serialised those classes.)

The rule is: the version of the code used in the transaction that spends a state needs to be >= any version of the input states. ``spending_version >= creation_version``

This rule needs to be enforced at verification time, and also during transaction building.

*Note:* This rule can be implemented as a normal contract constraint, as it is a constraint on the attachments that can be used on the spending transaction.
We don't currently support multiple constraints on a state and don't have any delegation mechanism to implement it like that.
 

#### Considered but decided against - Add Version field on TransactionState

The `version` could also be stored redundantly to states on the ledger - as a field in ``TransactionState`` and also in the `vault_states` table.

This would make the verification logic more clear and faster, and also expose the version of the input states to the contract verify code.

Note:

- When a transaction is verified the version of the attachment needs to match the version of the output states. 
 

## Actions

1. Implement the serialization `Strict Mode` and wire that during transaction verification, and more generally whenever it tries to deserialize ContractStates or Commands. 
Also to define other possible evolutions and decide if possible or not (E.g: adding/removing interfaces).
2. Find some good names for the `ContractJar` and the `FlowsJar`.
3. Implement the gradle plugin changes to split the 'cordapp' into 'contract' (better name) and 'flows' (better name).
4. Implement the versioning strategy proposed above in the 'contract' part of the plugin.
5. When importing a ContractJar, read the version and save it as a database column. Also add it as a field to teh `ContractAttachment` class. 
6. Implement the non-downgrade rule using the above `version`.
7. Add support to the `cordapp` plugin and the driver to test multiple versions of the contractJar together.
8. Document all the release process.
9. Update samples with the new gradle plugin.
10. Create an elaborate sample for some more complex flows and contract upgrade scenarios. 
Ideally with new fields added to the state, new version of the flow protocol, internal flow objects changed, subflows, dependency on other contracts.
This would be published as an example for how it should be done. 
11. Use this elaborate  sample to create a comprehensive automated Behave Compatibility Test Suite.

## Deferred tasks
   
1. Formalise the dependency rules between contracts, contracts and flows, and also subflow versioning.
2. Find a way to hide the complexity of backwards compatibility from flow devlopers (maybe using a handshake).
3. Remove custom contract state schema from ContractJar.

## Appendix:

This section contains various hypothetical scenarios to illustrate various issues and how they are solved by the current design.  

These are the possible changes:
 - changes to the state (fields added/removed from the state)
 - changes to the verification logic (more restrictive, less restrictive, or both - for different checks)
 - adding/removing/renaming of commands
 - Persistent Schema changes (These are out of scope of this document, as they shouldn't be in contract jar in the first place)
 - any combination of the above


Terminology:

- V1, V2 - are versions of the contract.
- Tx1, Tx2 - are transactions between parties.


### Scenario 1 - Spending a state with an older contract. 
- V1: com.megacorp.token.MegaToken(amount: Amount, owner: Party)
- V2: com.megacorp.token.MegaToken(amount: Amount, owner: Party, accumulatedDebt: Amount? = 0)


- Tx1: Alice transfers MegaToken to Bob, and selects V1 
- Tx2: Bob transfers to Chuck, but selects V2. The V1 output state will be deserialised with an accumulatedDebt=0, which is correct.
- After a while, Chuck accumulates some debt on this token.
- Txn: Chuck creates a transaction with Dan, but selects V1 as the contract version for this transaction, thus managing to "lose" the accumulatedDebt. (V1 does not know about the accumulatedDebt field)


Solution: This was analysed above. It will be solved by the non-downgrade rule and the serialization engine changes.


### Scenario 2: - Running an explicit upgrade  written against an older contract version.
- V1: com.megacorp.token.MegaToken(amount: Amount, owner: Party)
- V2: com.megacorp.token.MegaToken(amount: Amount, owner: Party, accumulatedDebt: Amount? = 0)
- Another company creates a better com.gigacorp.token.GigaToken that is an UpgradedContract designed to replace the MegaToken via an explicit upgrade, but develop against V1 ( as V2 was not released at the time of development).

Same as before:
- Tx1: Alice transfers MegaToken to Bob, and selects V1 (the only version available at that time)
- Tx2: Bob transfers to Chuck, but selects V2. The V1 output state will be deserialised with an accumulatedDebt=0, which is correct.
- After a while, Chuck accumulates some debt on this token.
- Chuck notices the GigaToken does not know about the accumulatedDebt field, so runs an explicit upgrade and transforms his MegaToken with debt into a clean GigaToken.

Solution: This attack breaks the assumption we made that contracts will not be adding fields that change it fundamentally.


### Scenario 3 - Flows installed by 2 peers compiled against different contract versions.
- Alice runs V1 of the MegaToken FlowsJAR, while Bob runs V2.
- Bob builds a new transaction where he transfers a state with accumulatedDebt To Alice.
- Alice is not able to correctly evaluate the business proposition, as she does not know that there even exists an accumulatedDebt field, but still signs it as if it was debt free.

Solution: Solved by the assumption that peer-to-peer communication can be lossy, and the peer with the higher version is responsible to send the right data


### Scenario 4 - Developer attempts to rename a field from one version to the next. 
- V1: com.megacorp.token.MegaToken(amount: Amount, owner: Party, accumulatedDebt: Amount )
- V2: com.megacorp.token.MegaToken(amount: Amount, owner: Party, currentDebt: Amount)

This is a rename of a field.  
This would break as soon as you try to spend a V1 state with V2, because there is no default value for currentDebt.

Solution: Not possible for now, as it breaks the serialisation evolution rules. Could be added as a new feature in the future.


### Scenario 5 - Contract verification logic becomes more strict in a new version. General concerns.
- V1: check that amount > 10
- V2: check that amount > 12 


- Tx1: Alice transfers MegaToken(amount=11) to Bob, and selects V1
- Tx2: Bob wants to forward it to Charlie, but in the meantime v2 is released, and Bob installs it.


- If Bob selects V2, the contract will fail. So, Bob needs to select V1, but will Charlie accept it?

The question is how important it is, that amount is >12?
 - Is it a new regulation active from a date?
 - Was it actually a (security) bug in V1?
 - Is it just a change done for not good reason?

Solution: This is not addressed in the current design doc. It needs to be explored in more depth.

### Scenario 6 - Contract verification logic becomes more strict in a new version. ???
- V1: check that amount > 10
- V2: check that amount > 12


- Alice runs V1 of the MegaToken FlowsJAR, while Bob runs V2. So Alice does not know (yet) that in the future the amount will have to be >12.
- Alice builds a transaction that transfers 11 token to Bob. This is a perfectly good transaction (from her point of view), with the V1 attachment.
- Should Bob sign this transaction?	
- This could be encoded in Bob's flow (who should know that the underlying contract has changed this way.). 

Solution: Same as Scenario 5.


### Scenario 7 - Contract verification logic becomes less strict.
- V1: check that amount > 12
- V2: check that amount > 10 

Alice runs V1 of the MegaToken FlowsJAR, while Bob runs V2.

Because there was no change in the actual structure of the state it means the flow that Alice has installed is compatible with the newer version from Bob so Alice could download V2 from Bob.

Solution: This should not require any change.


### Scenario 8 - Contract depends on another Contract.
- V1: com.megacorp.token.MegaToken(amount: Amount, owner: Party)
- V2: com.megacorp.token.MegaToken(amount: Amount, owner: Party, accumulatedDebt: Amount? = 0)

A contract developed by a thirdparty: com.megabank.tokens.SuperToken depends on com.megacorp.token.MegaToken

V1 of `com.megabank.tokens.SuperToken` is compiled against V1 of `com.megacorp.token.MegaToken`. So does not know about the new ``accumulatedDebt`` field.

Alice swaps MegaToken-v2 for SuperToken-v1 with Bob in a transaction. If Alice select v1 of the SuperToken contract attachment, then it will not be able to correctly evaluate the transaction. 


Solution: Solved by the assumption we made that contracts don't change fundamentally.


### Scenario 9 - A new command is added or removed 
- V1 of com.megacorp.token.MegaToken has 3 Commands: Issue, Move, Exit
- V2 of com.megacorp.token.MegaToken has 4 Commands: Issue, Move, Exit, AddDebt
- V3 of com.megacorp.token.MegaToken has 3 Commands: Issue, Move, Exit

There should not be any problem with adding/removing commands, as they apply only to transitions.
Spending of a state should not be affected by the command that created it.  

Solution: Does not require any change
