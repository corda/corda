Notaries
========

.. topic:: Summary

   * *Notaries prevent "double-spends"*
   * *Notaries may optionally also validate transactions*
   * *A network can have several notaries, each running a different consensus algorithm*

Notarisation
------------
A *notary* is a network service that provides **uniqueness consensus** by attesting that, for a given transaction, it
has not already signed other transactions that consumes any of the proposed transaction's input states.

Upon being sent asked to notarise a transaction, a notary will either:

* Sign the transaction if it has not already signed other transactions consuming any of the proposed transaction's
  input states
* Reject the transaction and flag that a double-spend attempt has occurred otherwise

In doing so, the notary provides the point of finality in the system. Until the notary's signature is obtained, parties
cannot be sure that an equally valid, but conflicting, transaction will not be regarded as the "valid" attempt to spend
a given input state. However, after the notary's signature is obtained, we can be sure that the proposed
transaction's input states had not already been consumed by a prior transaction. Hence, notarisation is the point
of finality in the system.

Every state has an appointed notary, and a notary will only notarise a transaction if it is the appointed notary
of all the transaction's input states.

Consensus algorithms
^^^^^^^^^^^^^^^^^^^^
Corda has "pluggable" consensus, allowing notaries to choose a consensus algorithm based on their requirements in
terms of privacy, scalability, legal-system compatibility and algorithmic agility.

In particular, notaries may differ in terms of:

* **Structure** - a notary may be a single network node, a cluster of mutually-trusting nodes, or a cluster of
  mutually-distrusting nodes
* **Consensus algorithm** - a notary service may choose to run a high-speed, high-trust algorithm such as RAFT, a
  low-speed, low-trust algorithm such as BFT, or any other consensus algorithm it chooses

Validation
^^^^^^^^^^
A notary service must also decide whether or not to provide **validity consensus** by validating each transaction
before committing it. In making this decision, they face the following trade-off:

* If a transaction **is not** checked for validity, it creates the risk of "denial of state" attacks, where a node
  knowingly builds an invalid transaction consuming some set of existing states and sends it to the
  notary, causing the states to be marked as consumed

* If the transaction **is** checked for validity, the notary will need to see the full contents of the transaction and
  its dependencies. This leaks potentially private data to the notary

There are several further points to keep in mind when evaluating this trade-off. In the case of the non-validating
model, Corda's controlled data distribution model means that information on unconsumed states is not widely shared.
Additionally, Corda's permissioned network means that the notary can store to the identity of the party that created
the "denial of state" transaction, allowing the attack to be resolved off-ledger.

In the case of the validating model, the use of anonymous, freshly-generated public keys instead of legal identities to
identify parties in a transaction limit the information the notary sees.

Multiple notaries
^^^^^^^^^^^^^^^^^
Each Corda network can have multiple notaries, each potentially running a different consensus algorithm. This provides
several benefits:

* **Privacy** - we can have both validating and non-validating notary services on the same network, each running a
  different algorithm. This allows nodes to choose the preferred notary on a per-transaction basis
* **Load balancing** - spreading the transaction load over multiple notaries allows higher transaction throughput for
  the platform overall
* **Low latency** - latency can be minimised by choosing a notary physically closer to the transacting parties

Changing notaries
^^^^^^^^^^^^^^^^^
Remember that a notary will only sign a transaction if it is the appointed notary of all of the transaction's input
states. However, there are cases in which we may need to change a state's appointed notary. These include:

* When a single transaction needs to consume several states that have different appointed notaries
* When a node would prefer to use a different notary for a given transaction due to privacy or efficiency concerns

Before these transactions can be created, the states must first be repointed to all have the same notary. This is
achieved using a special notary-change transaction that takes:

* A single input state
* An output state identical to the input state, except that the appointed notary has been changed

The input state's appointed notary will sign the transaction if it doesn't constitute a double-spend, at which point
a state will enter existence that has all the properties of the old state, but has a different appointed notary.