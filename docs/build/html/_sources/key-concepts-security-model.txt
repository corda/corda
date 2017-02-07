Security model
==============

Corda has been designed from the ground up to implement a global, decentralised database where all nodes are assumed to be
untrustworthy. This means that each node must actively cross-check each other's work to reach consensus
amongst a group of interacting participants.

The security model plays a role in the following areas:

* Identity:
  Corda is designed for semi-private networks in which admission requires obtaining an identity signed by a root authority.
  This assumption is pervasive – the flow API provides messaging in terms of identities, with routing and delivery to underlying nodes being handled automatically.
  See sections 3.2 of the `Technical white paper`_ for further details on identity and the permissioning service.

* Notarisation: pluggable notaries and algorithms offering different levels of trust.
  Notaries may be validating or non-validating. A validating notary will resolve and fully check transactions they are asked to deconflict.
  Without the use of any other privacy features, they gain full visibility into every transaction.
  On the other hand, non-validating notaries assume transaction validity and do not request transaction data or their dependencies
  beyond the list of states consumed (and thus, their level of trust is much lower and exposed to malicious use of transaction inputs).
  From an algorithm perspective, Corda currently provides a distributed notary implementation that uses Raft.

.. note:: Future notary algorithms may include BFT and hardware assisted non-BFT algorithms (where non-BFT algorithms
    are converted into a more trusted form using remote attestation and hardware protection).

* Authentication, authorisation and entitlements:
  Network permissioning, including node to node authentication, is performed using TLS and certificates.
  See :doc:`permissioning` for further detail.

.. warning:: API level authentication (RPC, Web) is currently simple username/password for demonstration purposes and will be revised.
    Similarly, authorisation is currently based on permission groups applied to flow execution.
    This is subject to design review with views to selecting a proven, mature entitlements solution.

Privacy techniques

* Partial data visibility: transactions are not globally broadcast as in many other systems.
* Transaction tear-offs: Transactions are structured as Merkle trees, and may have individual subcomponents be revealed to parties who already know the Merkle root hash. Additionally, they may sign the transaction without being able to see all of it.

    See :doc:`merkle-trees` for further detail.

* Multi-signature support: Corda uses composite keys to support scenarios where more than one key or party is required to authorise a state object transition.

.. note:: Future privacy techniques will include key randomisation, graph pruning, deterministic JVM sandboxing and support for secure signing devices.
    See sections 10 and 13 of the `Technical white paper`_ for detailed descriptions of these techniques and features.

.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf

