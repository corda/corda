.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing a custom notary service (experimental)
==============================================

.. warning:: Customising a notary service is still an experimental feature and not recommended for most use-cases. The APIs
   for writing a custom notary may change in the future. Additionally, customising Raft or BFT notaries is not yet
   fully supported. If you want to write your own Raft notary you will have to implement a custom database connector
   (or use a separate database for the notary), and use a custom configuration file.

Similarly to writing an oracle service, the first step is to create a service class in your CorDapp and annotate it
with ``@CordaService``. The Corda node scans for any class with this annotation and initialises them. The custom notary
service class should provide a constructor with two parameters of types ``AppServiceHub`` and ``PublicKey``.

.. container:: codeset

    .. code-block:: kotlin

        @CordaService
        class MyCustomValidatingNotaryService(override val services: AppServiceHub, override val notaryIdentityKey: PublicKey) : TrustedAuthorityNotaryService() {
          override val uniquenessProvider = PersistentUniquenessProvider(services.clock)

          override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = MyValidatingNotaryFlow(otherPartySession, this)

          override fun start() {}
          override fun stop() {}
        }

The next step is to write a notary service flow. You are free to copy and modify the existing built-in flows such
as ``ValidatingNotaryFlow``, ``NonValidatingNotaryFlow``, or implement your own from scratch (following the
``NotaryFlow.Service`` template). Below is an example of a custom flow for a *validating* notary service:

.. container:: codeset

    .. code-block:: kotlin

        class MyValidatingNotaryFlow(otherSide: FlowSession, service: MyCustomValidatingNotaryService) : NotaryServiceFlow(otherSide, service) {
            /**
            * The received transaction is checked for contract-validity, for which the caller also has to to reveal the whole
            * transaction dependency chain.
            */
            @Suspendable
            override fun validateRequest(requestPayload: NotarisationPayload): TransactionParts {
                try {
                    val stx = requestPayload.signedTransaction
                    validateRequestSignature(NotarisationRequest(stx.inputs, stx.id), requestPayload.requestSignature)
                    val notary = stx.notary
                    checkNotary(notary)
                    verifySignatures(stx)
                    resolveAndContractVerify(stx)
                    val timeWindow: TimeWindow? = if (stx.coreTransaction is WireTransaction) stx.tx.timeWindow else null
                    return TransactionParts(stx.id, stx.inputs, timeWindow, notary!!)
                } catch (e: Exception) {
                    throw when (e) {
                        is TransactionVerificationException,
                        is SignatureException -> NotaryInternalException(NotaryError.TransactionInvalid(e))
                        else -> e
                  }
                }
            }

            @Suspendable
            private fun resolveAndContractVerify(stx: SignedTransaction) {
                subFlow(ResolveTransactionsFlow(stx, otherSideSession))
                stx.verify(serviceHub, false)
                customVerify(stx)
            }

            private fun verifySignatures(stx: SignedTransaction) {
                val transactionWithSignatures = stx.resolveTransactionWithSignatures(serviceHub)
                checkSignatures(transactionWithSignatures)
            }

            private fun checkSignatures(tx: TransactionWithSignatures) {
                try {
                    tx.verifySignaturesExcept(service.notaryIdentityKey)
                } catch (e: SignatureException) {
                    throw NotaryInternalException(NotaryError.TransactionInvalid(e))
                }
            }

            private fun customVerify(stx: SignedTransaction) {
                // Add custom verification logic
            }
        }

To enable the service, add the following to the node configuration:

.. parsed-literal::

    notary : {
        validating : true # Set to false if your service is non-validating
        custom : true
    }