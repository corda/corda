API: Core types
===============

.. contents::

Corda provides several more core classes as part of its API.

SecureHash
----------
The ``SecureHash`` class represents a secure hash of unknown algorithm. We currently define only a single subclass,
``SecureHash.SHA256``. There are utility methods to create them, parse them and so on.

NamedByHash
-----------
Things which are identified by their hash, like transactions and attachments, should implement the ``NamedByHash``
interface which standardises how the ID is extracted. Note that a hash is *not* a globally unique identifier: it
is always a derivative summary of the contents of the underlying data.

Party
-----
Identities on the network are represented by ``AbstractParty``. There are two types of ``AbstractParty``:

* ``Party``, identified by a ``PublicKey`` and a ``CordaX500Name``

* ``AnonymousParty``, identified by a ``PublicKey`` only

For example, in a transaction sent to your node as part of a chain of custody it is important you can convince yourself
of the transaction's validity, but equally important that you don't learn anything about who was involved in that
transaction. In these cases ``AnonymousParty`` should be used. In contrast, for internal processing where extended
details of a party are required, the ``Party`` class should be used. The identity service provides functionality for
resolving anonymous parties to full parties.

CompositeKey
------------
Corda supports scenarios where more than one signature is required to authorise a state object transition. For example:
"Either the CEO or 3 out of 5 of his assistants need to provide signatures".

This is achieved using a ``CompositeKey``, which uses public-key composition to organise the various public keys into a
tree data structure. A ``CompositeKey`` is a tree that stores the cryptographic public key primitives in its leaves and
the composition logic in the intermediary nodes. Every intermediary node specifies a *threshold* of how many child
signatures it requires.

An illustration of an *"either Alice and Bob, or Charlie"* composite key:

.. image:: resources/composite-key.png
      :align: center
      :width: 300px

To allow further flexibility, each child node can have an associated custom *weight* (the default is 1). The *threshold*
then specifies the minimum total weight of all children required. Our previous example can also be expressed as:

.. image:: resources/composite-key-2.png
      :align: center
      :width: 300px

Signature verification is performed in two stages:

  1. Given a list of signatures, each signature is verified against the expected content.
  2. The public keys corresponding to the signatures are matched against the leaves of the composite key tree in question,
     and the total combined weight of all children is calculated for every intermediary node. If all thresholds are satisfied,
     the composite key requirement is considered to be met.