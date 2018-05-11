Tradeoffs
=========

.. topic:: Summary

   * *Permissioned networks are better suited for financial use-cases*
   * *Point-to-point communication allows information to be shared need-to-know*
   * *A UTXO model allows for more transactions-per-second*

Permissioned vs. permissionless
-------------------------------
Traditional blockchain networks are *permissionless*. The parties on the network are anonymous, and can join and
leave at will.

By contrast, Corda networks are *permissioned*. Each party on the network has a known identity that they use when
communicating with counterparties, and network access is controlled by a doorman. This has several benefits:

* Anonymous parties are inappropriate for most scenarios involving regulated financial institutions
* Knowing the identity of your counterparties allows for off-ledger resolution of conflicts using existing
  legal systems
* Sybil attacks are averted without the use of expensive mechanisms such as proof-of-work

Point-to-point vs. global broadcasts
------------------------------------
Traditional blockchain networks broadcast every message to every participant. The reason for this is two-fold:

* Counterparty identities are not known, so a message must be sent to every participant to ensure it reaches its
  intended recipient
* Making every participant aware of every transaction allows the network to prevent double-spends

The downside is that all participants see everyone else's data. This is unacceptable for many use-cases.

In Corda, each message is instead addressed to a specific counterparty, and is not seen by any uninvolved third
parties. The developer has full control over what messages are sent, to whom, and in what order. As a result, **data
is shared on a need-to-know basis only**. To prevent double-spends in this system, we employ notaries as
an alternative to proof-of-work.

Corda also uses several other techniques to maximize privacy on the network:

* **Transaction tear-offs**: Transactions are structured in a way that allows them to be digitally signed without
  disclosing the transaction's contents. This is achieved using a data structure called a Merkle tree. You can read
  more about this technique in :doc:`tutorial-tear-offs`.
* **Key randomisation**: The parties to a transaction are identified only by their public keys, and fresh keypairs are
  generated for each transaction. As a result, an onlooker cannot identify which parties were involved in a given
  transaction.

UTXO vs. account model
----------------------
Corda uses a *UTXO* (unspent transaction output) model. Each transaction consumes a set of existing states to produce
a set of new states.

The alternative would be an *account* model. In an account model, stateful objects are stored on-ledger, and
transactions take the form of requests to update the current state of these objects.

The main advantage of the UTXO model is that transactions with different inputs can be applied in parallel,
vastly increasing the network's potential transactions-per-second. In the account model, the number of
transactions-per-second is limited by the fact that updates to a given object must be applied sequentially.

Code-is-law vs. existing legal systems
--------------------------------------
Financial institutions need the ability to resolve conflicts using the traditional legal system where required. Corda
is designed to make this possible by:

* Having permissioned networks, meaning that participants are aware of who they are dealing with in every single
  transaction
* All code contracts are backed by a legal document describing the contract's intended behavior which can be relied
  upon to resolve conflicts

Build vs. re-use
----------------
Wherever possible, Corda re-uses existing technologies to make the platform more robust platform overall. For
example, Corda re-uses:

* Standard JVM programming languages for the development of CorDapps
* Existing SQL databases
* Existing message queue implementations