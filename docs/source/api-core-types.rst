API: Core types
===============

.. contents::

Corda provides several more core classes as part of its API.

SecureHash
----------
The ``SecureHash`` class is used to uniquely identify objects such as transactions and attachments by their hash.
Any object that needs to be identified by its hash should implement the ``NamedByHash`` interface:

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

``SecureHash`` is a sealed class that only defines a single subclass, ``SecureHash.SHA256``. There are utility methods
to create and parse ``SecureHash.SHA256`` objects.

.. _composite_keys:

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