Consensus Model
===============

The fundamental unit of consensus in Corda is the **state**. The concept of consensus can be divided into two parts:

1. Consensus over state **validity** -- parties can reach certainty that a transaction defining output states is accepted by the contract pointed to by the states and has all the required signatures. This is achieved by parties independently running the same contract code and validation logic (as described in :doc:`data model <data-model>`)

2. Consensus over state **uniqueness** -- parties can reach certainty the the output states created in a transaction are the unique successors to the input states consumed by that transaction (in other words -- a state has not been used as an input by more than one transaction)

This article presents an initial model for addressing the **uniqueness** problem.

.. note:: The current model is still a **work in progress** and everything described in this article can and is likely to change

Notary
------

We introduce the concept of a **Notary**, which is an authority responsible for attesting that for a given transaction, it had not signed another transaction consuming any of its input states. The data model is extended so that every **state** has an appointed Notary. All transactions have to be signed their the input state Notary for the output states to be **valid** (apart from *issue* transactions, containing no input states).

When the Notary is requested to sign a transaction, it either signs over it, attesting that the outputs are the **unique** successors of the inputs, or provides conflict information for any input state that had been consumed by another transaction it had signed before.

Validation
----------

The Notary *does not validate* transaction integrity (i.e. does not run contracts or check signatures) to minimise the exposed data. Validation would require the caller to reveal the whole transaction history chain, resulting in a privacy leak.

However, this makes it open to "denial of state" attacks, where a party could submit any invalid transaction to the Notary and thus "block" someone else's states. That is partially alleviated by requiring the calling party to authenticate and storing its identity for the request. The conflict information returned by the Notary specifies the consuming transaction id along with the identity of the party that had requested the commit. If the conflicting transaction is valid, the current one gets aborted; if not – a dispute can be raised and the input states of the conflicting invalid transaction are "un-committed" (to be covered by legal process).

.. note:: At present the Notary can see the entire transaction, but we have a separate piece of work to replace the parts of the transaction it does not require knowing about with hashes (only inputs and the timestamp command should be visible).

Multiple Notaries
-----------------

More than one Notary can exist in the network. This gives the following benefits:

* **Custom behaviour**. We can have both validating and privacy preserving Notaries -- parties can make a choice based on their specific requirements
* **Load balancing**. Spreading the transaction load over multiple Notaries will allow higher transaction throughput in the platform overall
* **Low latency**. Latency could be minimised by choosing a Notary physically closer the transacting parties

For fault tolerance each Notary can itself be a distributed entity, potentially a cluster maintained by mutually distrusting parties.

A transaction should only be signed by a Notary if all of its input states point to it. In cases where a transaction involves states controlled by multiple Notaries, the states first have to be repointed to the same notary. This is achieved by using a special type of transaction that doesn't modify anything but the Notary pointer of the state. Ensuring that all input states point to the same Notary is the responsibility of each involved party (it is another condition for an output state of the transaction to be **valid**)

Timestamping
------------

In this model the Notary also acts as a **Timestamping Authority**, verifying the transaction timestamp command.

For a timestamp to be meaningful, its implications must be binding on the party requesting it. A party obtains a timestamp signature to prove that some event happened before/at/or after a particular point in time, but without having to *commit* the transaction it has a choice of whether or not to reveal this fact until some point in the future. As a result, we need to ensure that the Notary either has to commit the transaction within some time tolerance, or perform timestamp validation and input state commit at the same time, which is the chosen behaviour for this model.