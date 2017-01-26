Consensus model
===============

The fundamental unit of consensus in Corda is the **state**. The concept of consensus can be divided into two parts:

1. Consensus over state **validity** -- parties can reach certainty that a transaction defining output states is accepted by the contracts pointed to by the states and has all the required signatures. This is achieved by parties independently running the same contract code and validation logic (as described in :doc:`data model <data-model>`)

2. Consensus over state **uniqueness** -- parties can reach certainty the output states created in a transaction are the unique successors to the input states consumed by that transaction (in other words -- a state has not been used as an input by more than one transaction)

This article presents an initial model for addressing the **uniqueness** problem.

.. note:: The current model is still a **work in progress** and everything described in this article can and is likely to change

Notary
------

We introduce the concept of a **notary**, which is an authority responsible for attesting that for a given transaction, it had not signed another transaction consuming any of its input states.
The data model is extended so that every **state** has an appointed notary:

.. sourcecode:: kotlin

    /**
     * A wrapper for [ContractState] containing additional platform-level state information.
     * This is the definitive state that is stored on the ledger and used in transaction outputs
     */
    data class TransactionState<out T : ContractState>(
            /** The custom contract state */
            val data: T,
            /** Identity of the notary that ensures the state is not used as an input to a transaction more than once */
            val notary: Party) {
        ...
    }

All transactions have to be signed by their input state notary for the output states to be **valid** (apart from *issue* transactions, containing no input states).

.. note:: The notary is a logical concept and can itself be a distributed entity, potentially a cluster maintained by mutually distrusting parties

When the notary is requested to sign a transaction, it either signs over it, attesting that the outputs are the **unique** successors of the inputs,
or provides conflict information for any input state that had been consumed by another transaction it had signed before.
In doing so, the notary provides the point of finality in the system. Until the notary signature is obtained, parties cannot be sure that an equally valid, but conflicting transaction,
will not be regarded as confirmed. After the signature is obtained, the parties know that the inputs to this transaction have been uniquely consumed by this transaction.
Hence it is the point at which we can say finality has occurred.

Multiple notaries
~~~~~~~~~~~~~~~~~

More than one notary can exist in the network. This gives the following benefits:

* **Custom behaviour**. We can have both validating and privacy preserving notaries -- parties can make a choice based on their specific requirements
* **Load balancing**. Spreading the transaction load over multiple notaries will allow higher transaction throughput in the platform overall
* **Low latency**. Latency could be minimised by choosing a notary physically closer the transacting parties

A transaction should only be signed by a notary if all of its input states point to it.
In cases where a transaction involves states controlled by multiple notaries, the states first have to be repointed to the same notary.
This is achieved by using a special type of transaction that doesn't modify anything but the notary pointer of the state.
Ensuring that all input states point to the same notary is the responsibility of each involved party
(it is another condition for an output state of the transaction to be **valid**)

Changing notaries
~~~~~~~~~~~~~~~~~

To change the notary for an input state, use the ``NotaryChangeFlow``. For example:

.. sourcecode:: kotlin

    @Suspendable
    fun changeNotary(originalState: StateAndRef<ContractState>,
                     newNotary: Party): StateAndRef<ContractState> {
        val flow = NotaryChangeFlow.Instigator(originalState, newNotary)
        return subFlow(flow)
    }

The flow will:

1. Construct a transaction with the old state as the input and the new state as the output

2. Obtain signatures from all *participants* (a participant is any party that is able to consume this state in a valid transaction, as defined by the state itself)

3. Obtain the *old* notary signature

4. Record and distribute the final transaction to the participants so that everyone possesses the new state

.. note:: Eventually this will be handled automatically on demand.

Validation
----------

One of the design decisions for a notary is whether or not to **validate** a transaction before committing its input states.

If a transaction is not checked for validity, it opens the platform to "denial of state" attacks, where anyone can build an invalid transaction consuming someone else's states and submit it to the notary to get the states "blocked".
However, validation of a transaction requires the notary to be able to see the full contents of the transaction in question and its dependencies.
This is an obvious privacy leak.

Our platform is flexible and we currently support both validating and non-validating notary implementations -- a party can select which one to use based on its own privacy requirements.

.. note:: In the non-validating model the "denial of state" attack is partially alleviated by requiring the calling
   party to authenticate and storing its identity for the request. The conflict information returned by the notary
   specifies the consuming transaction ID along with the identity of the party that had requested the commit. If the
   conflicting transaction is valid, the current one gets aborted; if not - a dispute can be raised and the input states
   of the conflicting invalid transaction are "un-committed" (to be covered by legal process).

.. note:: At present all notaries can see the entire contents of a transaction, but we have a separate piece of work to
   replace the parts of the transaction it does not require knowing about with hashes (only input references, timestamp
   information, overall transaction ID and the necessary digests of the rest of the transaction to prove that the
   referenced inputs/timestamps really do form part of the stated transaction ID should be visible).

Timestamping
------------

In this model the notary also acts as a *timestamping authority*, verifying the transaction timestamp command.

For a timestamp to be meaningful, its implications must be binding on the party requesting it.
A party can obtain a timestamp signature in order to prove that some event happened before/on/or after a particular point in time.
However, if the party is not also compelled to commit to the associated transaction, it has a choice of whether or not to reveal this fact until some point in the future.
As a result, we need to ensure that the notary either has to also sign the transaction within some time tolerance,
or perform timestamping *and* notarisation at the same time, which is the chosen behaviour for this model.

There will never be exact clock synchronisation between the party creating the transaction and the notary.
This is not only due to physics, network latencies, etc., but because between inserting the command and getting the
notary to sign there may be many other steps, like sending the transaction to other parties involved in the trade
as well, or even requesting human signoff. Thus the time observed by the notary may be quite different to the
time observed in step 1.

For this reason, times in transactions are specified as time *windows*, not absolute times. Time windows can be
open-ended, i.e. specify only one of "before" and "after" or they can be fully bounded. If a time window needs to
be converted to an absolute time for e.g. display purposes, there is a utility method on ``Timestamp`` to
calculate the mid point -- but in a distributed system there can never be "true time", only an approximation of it.

In this way we express that the *true value* of the fact "the current time" is actually unknowable. Even when both before and
after times are included, the transaction could have occurred at any point between those two timestamps. Here
"occurrence" could mean the execution date, the value date, the trade date etc ... the notary doesn't care what precise
meaning the timestamp has to the contract.

By creating a range that can be either closed or open at one end, we allow all of the following facts to be modelled:

* This transaction occurred at some point after the given time (e.g. after a maturity event)
* This transaction occurred at any time before the given time (e.g. before a bankruptcy event)
* This transaction occurred at some point roughly around the given time (e.g. on a specific day)

.. note:: It is assumed that the time feed for a notary is GPS/NaviStar time as defined by the atomic
   clocks at the US Naval Observatory. This time feed is extremely accurate and available globally for free.
