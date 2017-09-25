API: Confidential Identities
============================

.. note:: Before reading this page, you should be familiar with the core types from :doc:`api-core-types`, especially
the ``Party`` and related types.

.. contents::

Using Confidential Identities
-----------------------------

Confidential identities are key pairs where the corresponding X.509 certificate is not made public. Before constructing
a new transaction the parties must generate and exchange new confidential identities, a process which is typically
managed using ``SwapIdentitiesFlow``. These identities are then used when generating output states for the transaction,
and for signing commands based on output keys.

Where using outputs from a previous transaction in a new transaction, counterparties may need to know who the involved
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
