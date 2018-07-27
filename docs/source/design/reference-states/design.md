![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Design 

DOCUMENT MANAGEMENT
---

Design documents should follow the standard GitHub version management and pull request (PR) review workflow mechanism.

## Document Control

| Title                |                                          |
| -------------------- | ---------------------------------------- |
| Date                 | 27 March 2018                            |
| Author               | Roger Willis                             |
| Distribution         | Matthew Nesbit, Rick Parker              |
| Corda target version | open source and enterprise               |
| JIRA reference       | No JIRA's raised.                        |

## Approvals

#### Document Sign-off

| Author            |                                          |
| ----------------- | ---------------------------------------- |
| Reviewer(s)       | (GitHub PR reviewers)                    |
| Final approver(s) | (GitHub PR approver(s) from Design Approval Board) |

#### Design Decisions

There's only really one way to do this that satisfies our requirements - add a new input `StateAndRef` component group to the transaction classes. Other possible solutions are discussed below and why they are inappropriate.

## Document History 

* [Version 1](https://github.com/corda/enterprise/blob/779aaefa5c09a6a28191496dd45252b6e207b7f7/docs/source/design/reference-states/design.md) (Received comments from Richard Brown and Mark Oldfield).
* [Version 2](https://github.com/corda/enterprise/blob/a87f1dcb22ba15081b0da92ba1501b6b81ae2baf/docs/source/design/reference-states/design.md) (Version presented to the DRB).

HIGH LEVEL DESIGN
---

## Overview

See a prototype implementation here: https://github.com/corda/corda/pull/2889

There is an increasing need for Corda to support use-cases which require reference data which is issued and updated by specific parties, but available for use, by reference, in transactions built by other parties.

Why is this type of reference data required?

1. A key benefit of blockchain systems is that everybody is sure they see the same as their counterpart - and for this to work in situations where accurate processing depends on reference data requires everybody to be operating on the same reference data.
2. This, in turn, requires any given piece of reference data to be uniquely identifiable and, requires that any given transaction must be certain to be operating on the most current version of that reference data.
3. In cases where the latter condition applies, only the notary can attest to this fact and this, in turn, means the reference data must be in the form of an unconsumed state.

This document outlines the approach for adding support for this type of reference data to the Corda transaction model via a new approach called "reference input states".

## Background

Firstly, it is worth considering the types of reference data on Corda how it is distributed:

1. **Rarely changing universal reference data.** Such as currency codes and holiday calendars. This type of data can be added to transactions as attachments and referenced within contracts, if required. This data would only change based upon the decision of an International standards body, for example, therefore it is not critical to check the data is current each time it is used.
2. **Constantly changing reference data.** Typically, this type of data must be collected and aggregated by a central party. Oracles can be used as a central source of truth for this type of constantly changing data. There are multiple examples of making transaction validity contingent on data provided by Oracles (IRS demo and SIMM demo). The Oracle asserts the data was valid at the time it was provided.
3. **Periodically changing subjective reference data.** Reference data provided by entities such as bond issuers where the data changes frequently enough to warrant users of the data check it is current.

At present, periodically changing subjective data can only be provided via:

* Oracles,
* Attachments,
* Regular contract states, or alternatively,
* kept off-ledger entirely

However, neither of these solutions are optimal for reasons discussed in later sections of this design document.

As such, this design document introduces the concept of a "reference input state" which is a better way to serve "periodically changing subjective reference data" on Corda.

*What is a "reference input state"?*

A reference input state is a `ContractState` which can be referred to in a transaction by the contracts of input and output states but whose contract is not executed as part of the transaction verification process and is not consumed when the transaction is committed to the ledger but _is_ checked for "current-ness". In other words, the contract logic isn't run for the referencing transaction only. It's still a normal state when it occurs in an input or output position.

*What will reference input states enable?*

Reference data states will enable many parties to "reuse" the same state in their transactions as reference data whilst still allowing the reference data state owner the capability to update the state. When data distribution groups are available then reference state owners will be able to distribute updates to subscribers more easily. Currently, distribution would have to be performed manually.

*Roughly, how are reference input states implemented?*

Reference input states can be added to Corda by adding a new transaction component group that allows developers to add reference data `ContractState`s that are not consumed when the transaction is committed to the ledger. This eliminates the problems created by long chains of provenance, contention, and allows developers to use any `ContractState` for reference data. The feature should allow developers to add _any_ `ContractState` available in their vault, even if they are not a `participant` whilst nevertheless providing a guarantee that the state being used is the most recent version of that piece of information.

## Scope

Goals

* Add the capability to Corda transactions to support reference states

Non-goals (eg. out of scope)

* Data distribution groups are required to realise the full potential of reference data states. This design document does not discuss data distribution groups.

## Timeline

This work should be ready by the release of Corda V4. There is a prototype which is currently good enough for one of the firm's most critical projects, but more work is required:

* to assess the impact of this change
* write tests
* write documentation

## Requirements

1. Reference states can be any `ContractState` created by one or more `Party`s and subsequently updated by those `Party`s. E.g. `Cash`, `CompanyData`, `InterestRateSwap`, `FxRate`. Reference states can be `OwnableState`s, but it's more likely they will be `LinearState`s.
2. Any `Party` with a `StateRef` for a reference state should be able to add it to a transaction to be used as a reference, even if they are not a `participant` of the reference state.
3. The contract code for reference states should not be executed. However, reference data states can be referred to by the contracts of `ContractState`s in the input and output lists.
4. `ContractStates` should not be consumed when used as reference data.
5. Reference data must be current, therefore when reference data states are used in a transaction, notaries should check that they have not been consumed before.
6. To ensure determinism of the contract verification process, reference data states must be in scope for the purposes of transaction resolution. This is because whilst users of the reference data are not consuming the state, they must be sure that the series of transactions that created and evolved the state were executed validly.

**Use-cases:**

The canonical use-case for reference states: *KYC*

* KYC data can be distributed as reference data states.
* KYC data states can only updatable by the data owner.
* Usable by any party - transaction verification can be conditional on this KYC/reference data.
* Notary ensures the data is current.

Collateral reporting:

* Imagine a bank needs to provide evidence to another party (like a regulator) that they hold certain states, such as cash and collateral, for liquidity reporting purposes
* The regulator holds a liquidity reporting state that maintains a record of past collateral reports and automates the handling of current reports using some contract code
* To update the liquidity reporting state, the regulator needs to include the bank’s cash/collateral states in a transaction – the contract code checks available collateral vs requirements. By doing this, the cash/collateral states would be consumed, which is not desirable
* Instead, what if those cash/collateral states could be referenced in a transaction but not consumed? And at the same time, the notary still checks to see if the cash/collateral states are current, or not (i.e. does the bank still own them)

Other uses:

* Distributing reference data for financial instruments. E.g. Bond issuance details created, updated and distributed by the bond issuer rather than a third party.
* Account level data included in cash payment transactions.

## Design Decisions

There are various other ways to implement reference data on Corda, discussed below:

**Regular contract states**

Currently, the transaction model is too cumbersome to support reference data as unconsumed states for the following reasons:

* Contract verification is required for the `ContractState`s used as reference data. This limits the use of states, such as `Cash` as reference data (unless a special "reference" command is added which allows a "NOOP" state transaction to assert no that changes were made.)
* As such, whenever an input state reference is added to a transaction as reference data, an output state must be added, otherwise the state will be extinguished. This results in long chains of unnecessarily duplicated data.
* Long chains of provenance result in confidentiality breaches as down-stream users of the reference data state see all the prior uses of the reference data in the chain of provenance. This is an important point: it means that two parties, who have no business relationship and care little about each other's transactions nevertheless find themselves intimately bound: should one of them rely on a piece of common reference data in a transaction, the other one will not only need to be informed but will need to be furnished with a copy of the transaction.
* Reference data states will likely be used by many parties so they will be come highly contended. Parties will "race" to use the reference data. The latest copy must be continually distributed to all that require it.

**Attachments**

Of course, attachments can be used to store and share reference data. This approach does solve the contention issue around reference data as regular contract states. However, attachments don't allow users to ascertain whether they are working on the most recent copy of the data. Given that it's crucial to know whether reference data is current, attachments cannot provide a workable solution here.

The other issue with attachments is that they do not give an intrinsic "format" to data, like state objects do. This makes working with attachments much harder as their contents are effectively bespoke. Whilst a data format tool could be written, it's more convenient to work with state objects.

**Oracles**

Whilst Oracles could provide a solution for periodically changing reference data, they introduce unnecessary centralisation and are onerous to implement for each class of reference data. Oracles don't feel like an optimal solution here.

**Keeping reference data off-ledger**

It makes sense to push as much verification as possible into the contract code, otherwise why bother having it? Performing verification inside flows is generally not a good idea as the flows can be re-written by malicious developers. In almost all cases, it is much more difficult to change the contract code. If transaction verification can be conditional on reference data included in a transaction, as a state, then the result is a more robust and secure ledger (and audit trail).

## Target Solution

Changes required:

1. Add a `references` property of type `List<StateRef>` and `List<StateAndRef>` (for `FullTransaction`s) to all the transaction types.
2. Add a `REFERENCE_STATES` component group.
3. Amend the notary flows to check that reference states are current (but do not consume them)
4. Add a `ReferencedStateAndRef` class that encapsulates a `StateAndRef`, this is so `TransactionBuilder.withItems` can delineate between `StateAndRef`s and state references.
5. Add a `StateAndRef.referenced` method which wraps a `StateAndRef` in a `ReferencedStateAndRef`.
6. Add helper methods to `LedgerTransaction` to get `references` by type, etc.
7. Add a check to the transaction classes that asserts all references and inputs are on the same notary.
8. Add a method to `TransactionBuilder` to add a reference state.
9. Update the transaction resolution flow to resolve references.
10. Update the transaction and ledger DSLs to support references.
11. No changes are required to be made to contract upgrade or notary change transactions.

Implications:

**Versioning**

This can be done in a backwards compatible way. However, a minimum platform version must be mandated. Nodes running on an older version of Corda will not be able to verify transactions which include references. Indeed, contracts which refer to `references` will fail at run-time for older nodes.

**Privacy**

Reference states will be visible to all that possess a chain of provenance including them. There are potential implications from a data protection perspective here. Creators of reference data must be careful **not** to include sensitive personal data.

Outstanding issues:

**Oracle choice**

If the party building a transaction is using a reference state which they are not the owner of, they must move their states to the reference state's notary. If two or more reference states with different notaries are used, then the transaction cannot be committed as there is no notary change solution that works absent asking the reference state owner to change the notary.

This can be mitigated by requesting that reference state owners distribute reference states for all notaries. This solution doesn't work for `OwnableState`s used as reference data as `OwnableState`s should be unique. However, in most cases it is anticipated that the users of `OwnableState`s as reference data will be the owners of those states.

This solution introduces a new issue where nodes may store the same piece of reference data under different linear IDs. `TransactionBuilder`s would also need to know the required notary before a reference state is added.

**Syndication of reference states**

In the absence of data distribution groups, reference data must be manually transmitted to those that require it. Pulling might have the effect of DoS attacking nodes that own reference data used by many frequent users. Pushing requires reference data owners to be aware of all current users of the reference data. A temporary solution is required before data distribution groups are implemented.

Initial thoughts are that pushing reference states is the better approach.

**Interaction with encumbrances**

It is likely not possible to reference encumbered states unless the encumbrance state is also referenced. For example, a cash state referenced for collateral reporting purposes may have been "seized" and thus encumbered by a regulator, thus cannot be counted for the collateral report.

**What happens if a state is added to a transaction as an input as well as an input reference state?**

An edge case where a developer might erroneously add the same StateRef as an input state _and_ input reference state. The effect is referring to reference data that immediately becomes out of date! This is an edge case that should be prevented as it is likely to confuse CorDapp developers.

**Handling of update races.**

Usage of a referenced state may race with an update to it. This would cause notarisation failure, however, the flow cannot simply loop and re-calculate the transaction because it has not necessarily seen the updated tx yet (it may be a slow broadcast).

Therefore, it would make sense to extend the flows API with a new flow - call it WithReferencedStatesFlow that is given a set of LinearIDs and a factory that instantiates a subflow given a set of resolved StateAndRefs.

It does the following:

1. Checks that those linear IDs are in the vault and throws if not.
2. Resolves the linear IDs to the tip StateAndRefs.
3. Creates the subflow, passing in the resolved StateAndRefs to the factory, and then invokes it.
4. If the subflow throws a NotaryException because it tried to finalise and failed, that exception is caught and examined. If the failure was due to a conflict on a referenced state, the flow suspends until that state has been updated in the vault (there is an API to do wait for transaction already, but here the flow must wait for a state update).
5. Then it re-does the initial calculation, re-creates the subflow with the new resolved tips using the factory, and re-runs it as a new subflow.

Care must be taken to handle progress tracking correctly in case of loops.

## Complementary solutions

See discussion of alternative approaches above in the "design decisions" section.

## Final recommendation

Proceed to Implementation

TECHNICAL DESIGN
---

* Summary of changes to be included.
* Summary of outstanding issues to be included.