API: Confidential Identities
============================

Corda includes a number of

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

Well known identities are the publicly identifiable key of a legal entity or service, which makes them ill-suited
to transactions where confidentiality of participants is required. Although there are several elements to the Corda
transaction privacy model, such as ensuring transactions are only shared with those who need to see them, and use of
Intel SGX, it is important to provide defense in depth against privacy breaches. As such, nodes can create confidential
identities from their well known identity, which can only be used to identify the well known identity when provided along
with their X.509 certificate.

Using Confidential Identities
-----------------------------

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
