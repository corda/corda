Flows
=====

.. topic:: Summary

   * *Identities in Corda can represent legal identities or service identities*
   * *Identities are verified by X.509 certificate*
   * *Well known identities are stored in the network map and public*
   * *Confidential identities are shared only on a need to know basis*

Overview
--------

Identities in Corda can represent a legal identity (almost always an organisation), or a service identity. Service
identities are distinct to legal identities so that distributed services can exist on nodes owned by different
organisations (for example a distributed notary service).

Identities in Corda have a name, with is an X.500 distinguished name with a number of constraints imposed on the
attributes that are required and supported. These are paired with a public key to make a ``Party``, however at any
point multiple keys may be active for a single identity (the most common case being if a key is suspected compromised
and needs to be replaced).

X.509 certificates are used to prove the association of a name and key. Certificates for well known identities are
requested from the network Doorman service, and for confidential identities they are created by a node and signed by the
well known identity's key. Verification of identity certificates is handled by the IdentityService when registering
identities. The ``PartyAndCertificate`` data class is provided for cases where a party needs to be paired with its
certificate and path.

Name Structure
--------------

In order to be compatible with other implementations, we constrain the attributes to a subset of the minimum supported
set for X.509 certificates (specified in RFC 3280), plus the locality attribute:

* organization (O)
* state (ST)
* locality (L)
* country (C)
* organizational-unit (OU)
* common name (CN) - used only for service identities

The organisation, locality and country attributes are required, while state, organisational-unit and common name are
optional. Attributes cannot be be present more than once in the name. The "country" code is strictly restricted to valid
ISO 3166-1 two letter codes.

Confidential Identities
-----------------------

Well known identities are the publicly identifiable key of a legal entity or service, which makes them ill-suited
to transactions where confidentiality of participants is required. Although there are several elements to the Corda
transaction privacy model, such as ensuring transactions are only shared with those who need to see them, and use of
Intel SGX, it is important to provide defense in depth against privacy breaches. As such, nodes can create confidential
identities from their well known identity, which can only be used to identify the well known identity when provided along
with their X.509 certificate.

There are two key parts to using confidential identities:

* Before constructing a new transaction the parties must generate and exchange confidential identities, which is typically
  managed by ``SwapIdentitiesFlow``.
* Where using outputs from a previous transaction in a new transaction, counterparties may need to know who the involved
  parties are, for example proving that a well known identity owned some cash which it is using to pay a debt, where
  the owning key belongs to a confidential identity. ``IdentitySyncFlow`` can be used to extract parties involved in a
  transaction and allow a counterparty to request the certificates for any it does not recognise. Note that the
  ``CollectSignaturesFlow`` requires that the initiating node has signed the transaction, and as such all nodes providing
  signatures must recognise the signing key used by the initiating node as being either its well known identity or a
  confidential identity they have the certificate for.

Swap identities flow
~~~~~~~~~~~~~~~~~~~~

``SwapIdentitiesFlow`` takes the identity of a counterparty in its constructor, and is typically run as a subflow of
another flow. It returns a mapping from well known identities to confidential identities for the local and remote node;
in future this will be extended to handle swapping identities with multiple counterparties. You can see an example of it
being used in ``TwoPartyDealFlow.kt``:


.. container:: codeset

    .. code-block:: kotlin

        ...

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_ID
            val txIdentities = subFlow(SwapIdentitiesFlow(otherSideSession.counterparty))
            val anonymousMe = txIdentities[ourIdentity] ?: ourIdentity.anonymise()
            val anonymousCounterparty = txIdentities[otherSideSession.counterparty] ?: otherSideSession.counterparty.anonymise()

        ...

Identity synchronization flow
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When constructing a transaction whose input states reference confidential identities, it is common for other signing
entities (counterparties) to require to know which well known identities those confidential identities map to. The
``IdentitySyncFlow`` handles this process, and you can see an example of its use in ``TwoPartyTradeFlow.kt``:

.. container:: codeset

    .. code-block:: kotlin

        ...

            // Now sign the transaction with whatever keys we need to move the cash.
            val partSignedTx = serviceHub.signInitialTransaction(ptx, cashSigningPubKeys)

            // Sync up confidential identities in the transaction with our counterparty
            subFlow(IdentitySyncFlow.Send(sellerSession, ptx.toWireTransaction()))

            // Send the signed transaction to the seller, who must then sign it themselves and commit
            // it to the ledger by sending it to the notary.
            progressTracker.currentStep = COLLECTING_SIGNATURES
            val sellerSignature = subFlow(CollectSignatureFlow(partSignedTx, sellerSession, sellerSession.counterparty.owningKey))
            val twiceSignedTx = partSignedTx + sellerSignature


        ...
