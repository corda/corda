API: Identity
=============

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`key-concepts-identity`.

.. contents::

Party
-----
Identities on the network are represented by ``AbstractParty``. There are two types of ``AbstractParty``:

* ``Party``, identified by a ``PublicKey`` and a ``CordaX500Name``

* ``AnonymousParty``, identified by a ``PublicKey``

For example, in a transaction sent to your node as part of a chain of custody it is important you can convince yourself
of the transaction's validity, but equally important that you don't learn anything about who was involved in that
transaction. In these cases ``AnonymousParty`` should be used by flows constructing when transaction states and commands.
In contrast, for internal processing where extended details of a party are required, the ``Party`` class should be used
instead. The identity service provides functionality for flows to resolve anonymous parties to full parties, dependent
on the anonymous party's identity having been registered with the node earlier (typically this is handled by
``SwapIdentitiesFlow`` or ``IdentitySyncFlow``, discussed below).

Party names are held within the ``CordaX500Name`` data class, which enforces the structure of names within Corda, as
well as ensuring a consistent rendering of the names in plain text.

The support for both Party and AnonymousParty classes in Corda enables sophisticated selective disclosure of identity
information. For example, it is possible to construct a Transaction using an AnonymousParty, so nobody can learn of your
involvement by inspection of the transaction, yet prove to specific counterparts that this AnonymousParty actually is
owned by your well known identity. This disclosure is achieved through the use of the PartyAndCertificate data class
which can be propagated to those who need to know, and contains the Party's X.509 certificate path to provide proof of
ownership by a well known identity.

The PartyAndCertificate class is also used in the network map service to represent well known identities, in which
scenario the certificate path proves its issuance by the Doorman service.


Confidential Identities
-----------------------

Confidential identities are key pairs where the corresponding X.509 certificate (and path) are not made public, so that parties who
are not involved in the transaction cannot identify its participants. They are owned by a well known identity, which
must sign the X.509 certificate. Before constructing a new transaction the involved parties must generate and send new
confidential identities to each other, a process which is managed using ``SwapIdentitiesFlow`` (discussed below). The
public keys of these confidential identities are then used when generating output states and commands for the transaction.

Where using outputs from a previous transaction in a new transaction, counterparties may need to know who the involved
parties are. One example is in ``TwoPartyTradeFlow`` which delegates to ``CollectSignaturesFlow`` to gather certificates
from both parties. ``CollectSignaturesFlow`` requires that a confidential identity of the initiating node has signed
the transaction, and verifying this requires the receiving node has a copy of the confidential identity for the input
state. ``IdentitySyncFlow`` can be used to synchronize the confidential identities we have the certificate paths for, in
a single transaction, to another node.

.. note:: ``CollectSignaturesFlow`` requires that the initiating node has signed the transaction, and as such all nodes
   providing signatures must recognise the signing key used by the initiating node as being either its well known identity
   or a confidential identity they have the certificate for.

Swap identities flow
~~~~~~~~~~~~~~~~~~~~

``SwapIdentitiesFlow`` takes the party to swap identities with in its constructor (the counterparty), and is typically run as a subflow of
another flow. It returns a mapping from well known identities of the calling flow and our counterparty to the new
confidential identities; in future this will be extended to handle swapping identities with multiple parties.
You can see an example of it being used in ``TwoPartyDealFlow.kt``:

.. container:: codeset

    .. literalinclude:: ../../finance/src/main/kotlin/net/corda/finance/flows/TwoPartyDealFlow.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2
        :dedent: 8

The swap identities flow goes through the following key steps:

1. Generate a nonce value to form a challenge to the other nodes.
2. Send nonce value to all counterparties, and receive their nonce values.
3. Generate a new confidential identity from our well known identity.
4. Create a data blob containing the new confidential identity (public key, name and X.509 certificate path),
   and the hash of the nonce values.
5. Sign the resulting data blob with the confidential identity's private key.
6. Send the confidential identity and data blob signature to all counterparties, while receiving theirs.
7. Verify the signatures to ensure that identities were generated by the involved set of parties.
8. Verify the confidential identities are owned by the expected well known identities.
9. Store the confidential identities and return them to the calling flow.

This ensures not only that the confidential identity X.509 certificates are signed by the correct well known identities,
but also that the confidential identity private key is held by the counterparty, and that a party cannot claim ownership
another party's confidential identities belong to its well known identity.

Identity synchronization flow
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When constructing a transaction whose input states reference confidential identities, it is common for other signing
entities (counterparties) to require to know which well known identities those confidential identities map to. The
``IdentitySyncFlow`` handles distribution of a node's confidential identities, and you can see an example of its
use in ``TwoPartyTradeFlow.kt``:

.. container:: codeset

    .. literalinclude:: ../../finance/src/main/kotlin/net/corda/finance/flows/TwoPartyTradeFlow.kt
        :language: kotlin
        :start-after: DOCSTART 6
        :end-before: DOCEND 6
        :dedent: 12

The identity synchronization flow goes through the following key steps:

1. Extract participant identities from all input and output states. Filter this set down to confidential identities
   of the flow's well known identity. Required signers on commands are currently ignored as they are presumed to be
   included in the participants on states, or to be well known identities of services (such as an oracle service).
2. For each counterparty node, send a list of the public keys of the confidential identities, and receive back a list
   of those the counterparty needs the certificate path for.
3. Verify the requested list of identities contains only confidential identities in the offered list, and abort otherwise.
4. Send the requested confidential identities as ``PartyAndCertificate`` instances to the counterparty.

.. note:: ``IdentitySyncFlow`` works on a push basis. Receiving nodes can only request confidential identities being
   offered by the initiating node. There is no standard flow for nodes to collect
   confidential identities before assembling a transaction, and this is left for individual flows to manage if required.

``IdentitySyncFlow`` will serve only confidential identities in the provided transaction, limited to those that are
signed by the well known identity the flow is initiated by. This is done to avoid a risk of a node including
states it doesn't have the well known identity of participants in, to try convincing one of its counterparties to
reveal the identity. In case of a more complex transaction where multiple well known identities need confidential
identities distributed this flow should be run by each node in turn. For example:

* Alice is building the transaction, and provides some input state *x* owned by a confidential identity of Alice
* Bob provides some input state *y* owned by a confidential identity of Bob
* Charlie provides some input state *z* owned by a confidential identity of Charlie

Alice, Bob and Charlie must all run ``IdentitySyncFlow`` to send their involved confidential identities to the other
parties. For an illustration of the security implications of not requiring this, consider:

1. Alice is building the transaction, and provides some input state *x* owned by a confidential identity of Alice
2. Bob provides some input state *y* owned by a confidential identity it doesn't know the well known identity of, but
   Alice does.
3. Alice runs ``IdentitySyncFlow`` and sends not just their confidential identity, but also the confidential identity
   in state *y*, violating the privacy model.