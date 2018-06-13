Notaries
========

.. topic:: Summary

   * *Notary clusters prevent "double-spends"*
   * *Notary clusters may optionally also validate transactions*
   * *A network can have several notary clusters, each running a different consensus algorithm*

.. only:: htmlmode

    Video
    -----
    .. raw:: html
    
        <iframe src="https://player.vimeo.com/video/214138458" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
        <p></p>


Overview
--------
A *notary cluster* is a network service that provides **uniqueness consensus** by attesting that, for a given
transaction, it has not already signed other transactions that consumes any of the proposed transaction's input states.

Upon being sent asked to notarise a transaction, a notary cluster will either:

* Sign the transaction if it has not already signed other transactions consuming any of the proposed transaction's
  input states
* Reject the transaction and flag that a double-spend attempt has occurred otherwise

In doing so, the notary cluster provides the point of finality in the system. Until the notary cluster's signature is
obtained, parties cannot be sure that an equally valid, but conflicting, transaction will not be regarded as the
"valid" attempt to spend a given input state. However, after the notary cluster's signature is obtained, we can be sure
that the proposed transaction's input states have not already been consumed by a prior transaction. Hence, notarisation
is the point of finality in the system.

Every state has an appointed notary cluster, and a notary cluster will only notarise a transaction if it is the
appointed notary cluster of all the transaction's input states.

Consensus algorithms
--------------------
Corda has "pluggable" consensus, allowing notary clusters to choose a consensus algorithm based on their requirements in
terms of privacy, scalability, legal-system compatibility and algorithmic agility.

In particular, notary clusters may differ in terms of:

* **Structure** - a notary cluster may be a single node, several mutually-trusting nodes, or several
  mutually-distrusting nodes
* **Consensus algorithm** - a notary cluster may choose to run a high-speed, high-trust algorithm such as RAFT, a
  low-speed, low-trust algorithm such as BFT, or any other consensus algorithm it chooses

Validation
^^^^^^^^^^
A notary cluster must also decide whether or not to provide **validity consensus** by validating each transaction
before committing it. In making this decision, it faces the following trade-off:

* If a transaction **is not** checked for validity, it creates the risk of "denial of state" attacks, where a node
  knowingly builds an invalid transaction consuming some set of existing states and sends it to the
  notary cluster, causing the states to be marked as consumed

* If the transaction **is** checked for validity, the notary will need to see the full contents of the transaction and
  its dependencies. This leaks potentially private data to the notary cluster

There are several further points to keep in mind when evaluating this trade-off. In the case of the non-validating
model, Corda's controlled data distribution model means that information on unconsumed states is not widely shared.
Additionally, Corda's permissioned network means that the notary cluster can store the identity of the party that
created the "denial of state" transaction, allowing the attack to be resolved off-ledger.

In the case of the validating model, the use of anonymous, freshly-generated public keys instead of legal identities to
identify parties in a transaction limit the information the notary cluster sees.

Data visibility
^^^^^^^^^^^^^^^

Below is a summary of what specific transaction components have to be revealed to each type of notary:

+-----------------------------------+---------------+-----------------------+
| Transaction components            | Validating    | Non-validating        |
+===================================+===============+=======================+
| Input states                      | Fully visible | References only [1]_  |
+-----------------------------------+---------------+-----------------------+
| Output states                     | Fully visible | Hidden                |
+-----------------------------------+---------------+-----------------------+
| Commands (with signer identities) | Fully visible | Hidden                |
+-----------------------------------+---------------+-----------------------+
| Attachments                       | Fully visible | Hidden                |
+-----------------------------------+---------------+-----------------------+
| Time window                       | Fully visible | Fully visible         |
+-----------------------------------+---------------+-----------------------+
| Notary identity                   | Fully visible | Fully visible         |
+-----------------------------------+---------------+-----------------------+
| Signatures                        | Fully visible | Hidden                |
+-----------------------------------+---------------+-----------------------+

Both types of notaries record the calling party's identity: the public key and the X.500 Distinguished Name.

.. [1] A state reference is composed of the issuing transaction's id and the state's position in the outputs. It does not
   reveal what kind of state it is or its contents.

Multiple notaries
-----------------
Each Corda network can have multiple notary clusters, each potentially running a different consensus algorithm. This
provides several benefits:

* **Privacy** - we can have both validating and non-validating notary clusters on the same network, each running a
  different algorithm. This allows nodes to choose the preferred notary cluster on a per-transaction basis
* **Load balancing** - spreading the transaction load over multiple notary clusters allows higher transaction
  throughput for the platform overall
* **Low latency** - latency can be minimised by choosing a notary cluster physically closer to the transacting parties

Changing notaries
^^^^^^^^^^^^^^^^^
Remember that a notary cluster will only sign a transaction if it is the appointed notary cluster of all of the
transaction's input states. However, there are cases in which we may need to change a state's appointed notary cluster.
These include:

* When a single transaction needs to consume several states that have different appointed notary clusters
* When a node would prefer to use a different notary cluster for a given transaction due to privacy or efficiency
  concerns

Before these transactions can be created, the states must first all be repointed to the same notary cluster. This is
achieved using a special notary-change transaction that takes:

* A single input state
* An output state identical to the input state, except that the appointed notary cluster has been changed

The input state's appointed notary cluster will sign the transaction if it doesn't constitute a double-spend, at which
point a state will enter existence that has all the properties of the old state, but has a different appointed notary
cluster.