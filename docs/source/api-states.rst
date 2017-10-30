API: States
===========

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-states`.

.. contents::

ContractState
-------------
In Corda, states are instances of classes that implement ``ContractState``. The ``ContractState`` interface is defined
as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/ContractState.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

``ContractState`` has a single field, ``participants``. ``participants`` is a ``List`` of the ``AbstractParty`` that
are considered to have a stake in the state. Among other things, the ``participants`` will:

* Usually store the state in their vault (see below)

* Need to sign any notary-change and contract-upgrade transactions involving this state

* Receive any finalised transactions involving this state as part of ``FinalityFlow``

ContractState sub-interfaces
----------------------------
The behaviour of the state can be further customised by implementing sub-interfaces of ``ContractState``. The two most
common sub-interfaces are:

* ``LinearState``

* ``OwnableState``

``LinearState`` models shared facts for which there is only one current version at any point in time. ``LinearState``
states evolve in a straight line by superseding themselves. On the other hand, ``OwnableState`` is meant to represent
assets that can be freely split and merged over time. Cash is a good example of an ``OwnableState`` - two existing $5
cash states can be combined into a single $10 cash state, or split into five $1 cash states. With ``OwnableState``, its
the total amount held that is important, rather than the actual units held.

We can picture the hierarchy as follows:

.. image:: resources/state-hierarchy.png

.. note:: These interfaces are used by the vault to decide which states to store. By default the vault stores all states
   on which it is a participant. For states which implement ``OwnableState`` it normally only retains the state if it is the
   owner. The exception to these two cases is if a state implements ``ObservedState``, in which case the vault will also
   keep a copy of the state if vault belongs to an observer of the state.

LinearState
^^^^^^^^^^^
The ``LinearState`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2

Remember that in Corda, states are immutable and can't be updated directly. Instead, we represent an evolving fact as a
sequence of ``LinearState`` states that share the same ``linearId`` and represent an audit trail for the lifecycle of
the fact over time.

When we want to extend a ``LinearState`` chain (i.e. a sequence of states sharing a ``linearId``), we:

* Use the ``linearId`` to extract the latest state in the chain from the vault

* Create a new state that has the same ``linearId``

* Create a transaction with:

  * The current latest state in the chain as an input

  * The newly-created state as an output

The new state will now become the latest state in the chain, representing the new current state of the agreement.

``linearId`` is of type ``UniqueIdentifier``, which is a combination of:

* A Java ``UUID`` representing a globally unique 128 bit random number
* An optional external-reference string for referencing the state in external systems

OwnableState
^^^^^^^^^^^^
The ``OwnableState`` interface is defined as follows:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 3
        :end-before: DOCEND 3

Where:

* ``owner`` is the ``PublicKey`` of the asset's owner

* ``withNewOwner(newOwner: AbstractParty)`` creates an copy of the state with a new owner

Because ``OwnableState`` models fungible assets that can be merged and split over time, ``OwnableState`` instances do
not have a ``linearId``. $5 of cash created by one transaction is considered to be identical to $5 of cash produced by
another transaction.

Other interfaces
^^^^^^^^^^^^^^^^
You can also customize your state by implementing the following interfaces:

* ``QueryableState``, which allows the state to be queried in the node's database using custom attributes (see
  :doc:`api-persistence`)

* ``SchedulableState``, which allows us to schedule future actions for the state (e.g. a coupon payment on a bond) (see
  :doc:`event-scheduling`)

* ``ObservedState``, which allows observers for a state to be defined, separate to the participants. These are parties
  which are interested in the state but are not directly involved, such as a regulator. It is strongly encouraged that
  nodes which are notified of the creation of a state are also notified when it is consumed, so that they are aware of
  the lifecycle of the state and can use this information to decide to archive or delete legacy states depending on
  local policies.

User-defined fields
-------------------
Beyond implementing ``ContractState`` or a sub-interface, a state is allowed to have any number of additional fields
and methods. For example, here is the relatively complex definition for a state representing cash:

.. container:: codeset

    .. literalinclude:: ../../finance/src/main/kotlin/net/corda/finance/contracts/asset/Cash.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

The vault
---------
Whenever a node records a new transaction, it also decides whether it should store each of the transaction's output
states in its vault. The default vault implementation makes the decision based on the following rules:

  * If the state is an ``OwnableState``, the vault will store the state if the node is the state's ``owner``
  * Otherwise, the vault will store the state if it is one of the ``participants``

States that are not considered relevant are not stored in the node's vault. However, the node will still store the
transactions that created the states in its transaction storage.

TransactionState
----------------
When a ``ContractState`` is added to a ``TransactionBuilder``, it is wrapped in a ``TransactionState``:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/TransactionState.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

Where:

* ``data`` is the state to be stored on-ledger
* ``contract`` is the contract governing evolutions of this state
* ``notary`` is the notary service for this state
* ``encumbrance`` points to another state that must also appear as an input to any transaction consuming this
  state
* ``constraint`` is a constraint on which contract-code attachments can be used with this state