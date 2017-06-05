API: States
===========

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-states`.

ContractState
-------------
In Corda, states are classes that implement ``ContractState``. The ``ContractState`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

Where:

* ``contract`` is the ``Contract`` class defining the constraints on transactions involving states of this type
* ``participants`` is a ``List`` of the ``AbstractParty`` who are considered to have a stake in the state. For example,
  all the ``participants`` will:

  * Need to sign a notary-change transaction for this state
  * Receive any committed transactions involving this state as part of ``FinalityFlow``

The vault
---------
Each node has a vault, where it stores the states that are "relevant" to the node's owner. Whenever the node sees a
new transaction, it performs a relevancy check to decide whether to add each of the transaction's output states to
its vault. The default vault implementation decides whether a state is relevant as follows:

  * The vault will store any state for which it is one of the ``participants``
  * This behavior is overridden for states that implement ``LinearState`` or ``OwnableState`` (see below)

If a state is not considered relevant, the node will still store the transaction in its local storage, but it will
not track the transaction's states in its vault.

ContractState sub-interfaces
----------------------------
There are two common optional sub-interfaces of ``ContractState``:

* ``LinearState``, which helps represent objects that have a constant identity over time
* ``OwnableState``, which helps represent fungible assets

For example, a cash is an ``OwnableState`` - you don't have a specific piece of cash you are tracking over time, but
rather a total amount of cash that you can combine and divide at will. A contract, on the other hand, cannot be
merged with other contracts of the same type - it has a unique separate identity over time.

We can picture the hierarchy as follows:

.. image:: resources/state-hierarchy.png

LinearState
^^^^^^^^^^^
``LinearState`` models facts that have a constant identity over time. Remember that in Corda, states are immutable and
can't be updated directly. Instead, we represent an evolving fact as a sequence of states where every state is a
``LinearState`` that shares the same ``linearId``. Each sequence of linear states represents the lifecycle of a given
fact up to the current point in time. It represents the historic audit trail of how the fact evolved over time to its
current "state".

The ``LinearState`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2

Where:

* ``linearId`` is a ``UniqueIdentifier`` that:

  * Allows the successive versions of the fact to be linked over time
  * Provides an ``externalId`` for referencing the state in external systems

* ``isRelevant(ourKeys: Set<PublicKey>)`` overrides the default vault implementation's relevancy check. You would
  generally override it to check whether ``ourKeys`` is relevant to the state at hand in some way.

The vault tracks the head (i.e. the most recent version) of each ``LinearState`` chain (i.e. each sequence of
states all sharing a ``linearId``). To create a transaction updating a ``LinearState``, we retrieve the state from the
vault using its ``linearId``.

OwnableState
^^^^^^^^^^^^
``OwnableState`` models fungible assets. Fungible assets are assets for which it's the total amount held that is
important, rather than the actual units held. US dollars are an example of a fungible asset - we do not track the
individual dollar bills held, but rather the total amount of dollars.

The ``OwnableState`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 3
        :end-before: DOCEND 3

Where:

* ``owner`` is the ``PublicKey`` of the asset's owner

  * ``OwnableState`` also override the default behavior of the vault's relevancy check. The default vault
  implementation will track any ``OwnableState`` of which it is the owner.

* ``withNewOwner(newOwner: PublicKey)`` creates an identical copy of the state, only with a new owner

Other interfaces
^^^^^^^^^^^^^^^^
``ContractState`` has several more sub-interfaces that can optionally be implemented:

* ``QueryableState``, which allows the state to be queried in the node's database using SQL (see
  :doc:`persistence`)
* ``SchedulableState``, which allows us to schedule future actions for the state (e.g. a coupon on a bond) (see
  :doc:`event-scheduling`)

User-defined fields
-------------------
Beyond implementing ``LinearState`` or ``OwnableState``, the definition of the state is up to the CorDapp developer.
You can define any additional class fields and methods you see fit.

For example, here is a relatively complex state definition, for a state representing cash:

.. container:: codeset

    .. literalinclude:: ../../finance/src/main/kotlin/net/corda/contracts/asset/Cash.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

TransactionState
----------------
When a ``ContractState`` is added to a ``TransactionBuilder``, it is wrapped in a ``TransactionState``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 4
        :end-before: DOCEND 4

Where:

* ``data`` is the state to be stored on-ledger
* ``notary`` is the notary service for this state
* ``encumbrance`` points to another state that must also appear as an input to any transaction consuming this
  state