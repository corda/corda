API: Core types
===============

Corda provides a large standard library of data types used to represent the Corda data model. In addition, there are a
series of helper libraries which provide date manipulation, maths and cryptography functions.

Cryptography and maths support
------------------------------
The ``SecureHash`` class represents a secure hash of unknown algorithm. We currently define only a single subclass,
``SecureHash.SHA256``. There are utility methods to create them, parse them and so on.

We also provide some mathematical utilities, in particular a set of interpolators and classes for working with
splines. These can be found in the `maths package <api/kotlin/corda/net.corda.core.math/index.html>`_.

NamedByHash and UniqueIdentifier
--------------------------------
Things which are identified by their hash, like transactions and attachments, should implement the ``NamedByHash``
interface which standardises how the ID is extracted. Note that a hash is *not* a globally unique identifier: it
is always a derivative summary of the contents of the underlying data. Sometimes this isn't what you want:
two deals that have exactly the same parameters and which are made simultaneously but which are logically different
can't be identified by hash because their contents would be identical.

Instead you would use  ``UniqueIdentifier``. This is a combination of a (Java) ``UUID`` representing a globally
unique 128 bit random number, and an arbitrary string which can be paired with it. For instance the string may
represent an existing "weak" (not guaranteed unique) identifier for convenience purposes.

Party and CompositeKey
----------------------
Entities using the network are called *parties*. Parties can sign structures using keys, and a party may have many
keys under their control.

Parties can be represented either in full (including name) or pseudonymously, using the ``Party`` or ``AnonymousParty``
classes respectively. For example, in a transaction sent to your node as part of a chain of custody it is important you
can convince yourself of the transaction's validity, but equally important that you don't learn anything about who was
involved in that transaction. In these cases ``AnonymousParty`` should be used, which contains a public key (may be a composite key)
without any identifying information about who owns it. In contrast, for internal processing where extended details of
a party are required, the ``Party`` class should be used. The identity service provides functionality for resolving
anonymous parties to full parties.

.. note:: These types are provisional and will change significantly in future as the identity framework becomes more
fleshed out.

CommandWithParties
------------------
A ``CommandWithParties`` represents a command and the list of associated signers' identities.

Multi-signature support
-----------------------
Corda supports scenarios where more than one key or party is required to authorise a state object transition, for example:
"Either the CEO or 3 out of 5 of his assistants need to provide signatures".

.. _composite-keys:

Composite Keys
^^^^^^^^^^^^^^
This is achieved by public key composition, using a tree data structure ``CompositeKey``. A ``CompositeKey`` is a tree that
stores the cryptographic public key primitives in its leaves and the composition logic in the intermediary nodes. Every intermediary
node specifies a *threshold* of how many child signatures it requires.

An illustration of an *"either Alice and Bob, or Charlie"* composite key:

.. image:: resources/composite-key.png
      :align: center
      :width: 300px

To allow further flexibility, each child node can have an associated custom *weight* (the default is 1). The *threshold*
then specifies the minimum total weight of all children required. Our previous example can also be expressed as:

.. image:: resources/composite-key-2.png
      :align: center
      :width: 300px

Verification
^^^^^^^^^^^^
Signature verification is performed in two stages:

  1. Given a list of signatures, each signature is verified against the expected content.
  2. The public keys corresponding to the signatures are matched against the leaves of the composite key tree in question,
     and the total combined weight of all children is calculated for every intermediary node. If all thresholds are satisfied,
     the composite key requirement is considered to be met.

Date support
------------
There are a number of supporting interfaces and classes for use by contracts which deal with dates (especially in the
context of deadlines). As contract negotiation typically deals with deadlines in terms such as "overnight", "T+3",
etc., it's desirable to allow conversion of these terms to their equivalent deadline. ``Tenor`` models the interval
before a deadline, such as 3 days, etc., while ``DateRollConvention`` describes how deadlines are modified to take
into account bank holidays or other events that modify normal working days.

Calculating the rollover of a deadline based on working days requires information on the bank holidays involved
(and where a contract's parties are in different countries, for example, this can involve multiple separate sets of
bank holidays). The ``BusinessCalendar`` class models these calendars of business holidays; currently it loads these
from files on disk, but in future this is likely to involve reference data oracles in order to ensure consensus on the
dates used.
