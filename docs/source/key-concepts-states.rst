States
======

.. topic:: Summary

   * *States represent facts that exist on the ledger*
   * *States are immutable objects*
   * *States are evolved by marking the current state as historic and creating an updated state*
   * *The lifecycle of a shared fact over time is represented by a state sequence, which comprises the current unconsumed state, plus all consumed historical states*
   * *Each node has a vault to store the states it knows about*

.. only:: htmlmode

   Video
   -----
   .. raw:: html
   
       <iframe src="https://player.vimeo.com/video/213812054" width="640" height="360" frameborder="0" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>
       <p></p>


States Represent (Shared) Facts
--------
﻿A **state** is an immutable object that represents a fact known by one or more Corda Nodes at a particular point in time. States can contain arbitrary data, allowing them to represent facts of any kind. 

States usually represent agreements or assets; for example, they may represent stocks, cash, bonds, loans, KYC data, identity information and so on.

Example: A state that represents an IOU between Alice and Bob (Alice owes Bob £10).

.. image:: resources/state.png
   :scale: 25%
   :align: center

The preceding diagram describes a state that represents an IOU. The IOU is an agreement for Alice to pay Bob £10 by the 1st of March 2017. 

As well as information about the fact itself (the state's properties), the state also describes the participating nodes, and contains a reference to the contract that governs the evolution of the state over time. ﻿﻿

For more information, see :doc:`key-concepts-contracts`.

State Evolution
---------------

﻿States contain data about shared facts at a specific point in time. They cannot be changed once they are created; that is, they are `immutable <https://en.wikipedia.org/wiki/Immutable_object>`_.

However, this conflicts with the nature of agreements or shared facts, as they are expected to "evolve" over time in response to real-world events. (In the previous example, Alice might pay a certain amount of the IOU, in which case we would expect the shared fact to change.)

**How can states evolve in response to real-world events?**

Old states are replaced with new ones. The new state is created by copying the old one and updating its properties as required. There is only one latest version at any point in time. Once a new state is created, the old one is marked as historic.

**Note:** ﻿Historic states are not considered "on ledger", but they do remain accessible (they are stored in the vault). This record of historic states provides a useful audit trail. 

State Sequence
---------------
As states are immutable, they cannot be modified directly to reflect a change in the state of the world.

Instead, the lifecycle of a shared fact over time is represented by a **state sequence**. The state sequence is the chain of historic states leading to the current state. 

Example: A state representing Alice and Bob's IOU is created and stored in the vault. When Alice pays £5, a new version of the state is created and the old version is marked as historic.

.. image:: resources/state-sequence.png
   :scale: 25%
   :align: center
   
**Note:** We say that, when a state is replaced in the sequence, the old state is consumed. The current state is unconsumed (it is yet to be replaced). 

The Vault
---------
Each node on the network maintains a *vault*; that is, a database where it tracks all the current and historic states that it
is aware of, and which it considers to be relevant to itself.

Example: A simplified representation of the current (unconsumed) and historic (consumed) states in the vault.

.. image:: resources/vault-simple.png
   :scale: 25%
   :align: center

* The ledger is the set of all the current (that is, non-historic) states that the node is party to. 
* The vault stores the states that comprise the whole state sequence. That is, the current state and all of the historic versions of those states.

**Terminology:** The "head" of a state sequence the current state of a shared fact. The current state of the ledger comprises all of the heads of the state sequences. 

Reference States
----------------

Not all states need to be updated by the parties that use them. In the case of reference data, there is a common pattern
where one party creates reference data, which is then used (but not updated) by other parties. 

In this use case, the states containing reference data are referred to as **reference states**. Syntactically, reference states are no different
to regular states. However, they are treated differently by Corda transactions. 

For more information, see :doc:`key-concepts-transactions`
