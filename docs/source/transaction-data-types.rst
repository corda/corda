Transaction Data Types
======================

There is a large library of data types used in Corda transactions and contract state objects.

Amount
------

The ``Amount`` class is used to represent an amount of some fungible asset. It is a generic class which wraps around
a type used to define the underlying asset, for example a ``TokenDefinition``, or this can be a more complex type
such as an obligation contract issuance definition (which in turn contains a token definition for whatever the obligation
is to be settled in).

.. note:: Fungible is used here to mean that instances of an asset is interchangeable for any other identical instance,
          and that they can be split/merged. For example a £5 note can reasonably be exchanged for any other £5 note, and a
          £10 note can be exchanged for two £5 notes, or vice-versa.

Where a contract refers directly to an amount of something, ``Amount`` should wrap ``TokenDefinition``, which in
turn can refer to a ``Currency`` (GBP, USD, CHF, etc.), or any other class. Future work in this area will include
introducing classes to represent non-currency things (such as commodities) that TokenDefinition can wrap. For more
complex amounts, ``Amount`` can wrap other types, for example to represent a number of Obligation contracts to be
delivered (themselves referring to a currency), an ``Amount`` such as the following would used:

.. container:: codeset

   .. sourcecode:: kotlin

      Amount<Obligation.State<Currency>>

Contract State
--------------

A Corda contract is composed of three parts; the executable code, the legal prose, and the state objects that represent
the details of the contract (see :doc:`data-model` for further detail). States essentially convert the generic template
(code and legal prose) into a specific instance. In a ``WireTransaction``, outputs are provided as ``ContractState``
implementations, while the inputs are references to the outputs of a previous transaction. These references are then
stored as ``StateRef`` objects, which are converted to ``StateAndRef`` on demand.

A number of interfaces then extend ``ContractState``, representing standardised functionality for states:

  ``OwnableState``
    A state which has an owner (represented as a ``PublicKey``, discussed later). Exposes the owner and a function for
    replacing the owner.

  ``LinearState``
    A state which links back to its previous state, creating a thread of states over time. Intended to simplify tracking
    state versions.

  ``DealState``
    A state representing an agreement between two or more parties. Intended to simplify implementing generic protocols
    that manipulate many agreement types.

  ``FixableDealState``
    A deal state, with further functions exposed to support fixing of interest rates.

Things (such as attachments) which are identified by their hash should implement the ``NamedByHash`` interface,
which standardises how the ID is extracted.

FungibleAssets and Cash
-----------------------

There is a common ``FungibleAsset`` superclass for contracts which model fungible assets, which also provides a standard
interface for its subclasses' state objects to implement. The clear use-case is ``Cash``, however ``FungibleAsset`` is
intended to be readily extensible to cover other assets, for example commodities could be modelled by using a subclass
whose state objects include further details (location of the commodity, origin, grade, etc.) as needed.

Transaction Types
-----------------

The ``WireTransaction`` class contains the core of a transaction without signatures, and with references to attachments
in place of the attachments themselves (see also :doc:`data-model`). Once signed these are encapsulated in the
``SignedTransaction`` class. For processing a transaction (i.e. to verify it) it is first converted to a
``LedgerTransaction``, which involves verifying the signatures and associating them to the relevant command(s), and
resolving the attachment references to the attachments. Commands with valid signatures are encapsulated in the
``AuthenticatedObject`` type.

Party and PublicKey
-------------------

Identities of parties involved in signing a transaction can be represented simply by their ``PublicKey``, or by further
information (such as name) using the ``Party`` class. An ``AuthenticatedObject`` contains a list of the public keys
for signatures present on the transaction, as well as list of parties for those public keys (where known).

Date Support
------------

There are a number of supporting interfaces and classes for use by contract which deal with dates (especially in the
context of deadlines). As contract negotiation typically deals with deadlines in terms such as "overnight", "T+3",
etc., it's desirable to allow conversion of these terms to their equivalent deadline. ``Tenor`` models the interval
before a deadline, such as 3 days, etc., while ``DateRollConvention`` describes how deadlines are modified to take
into account bank holidays or other events that modify normal working days.

Calculating the rollover of a deadline based on working days requires information on the bank holidays involved
(and where a contract's parties are in different countries, for example, this can involve multiple separate sets of
bank holidays). The ``BusinessCalendar`` class models these calendars of business holidays; currently it loads these
from files on disk, but in future this is likely to involve reference data oracles in order to ensure consensus on the
dates used.
