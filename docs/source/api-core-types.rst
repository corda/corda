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

ByteSequence
------------
An abstraction of a byte array, with offset and size that does no copying of bytes unless asked to.
The data of interest typically starts at position [offset] within the [bytes] and is [size] bytes long.

OpaqueBytes
-----------
A simple class that wraps a ByteSequence and makes the equals/hashCode/toString methods work a value type.
The bytes are always cloned so that this object becomes immutable. This has been done
     * to prevent tampering with entities such as [SecureHash] and [PrivacySalt], as well as
     * preserve the integrity of our hash constants [zeroHash] and [allOnesHash].
This type has a number of helper methods fot conversion to/from [ByteArray] and [OpaqueBytes].

.. container:: codeset

    .. literalinclude:: ../../core/src/main/kotlin/net/corda/core/contracts/Structures.kt
        :language: kotlin
        :start-after: DOCSTART 9
        :end-before: DOCEND 9

