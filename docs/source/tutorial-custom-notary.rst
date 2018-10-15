.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing a custom notary service (experimental)
==============================================

.. warning:: Customising a notary service is still an experimental feature and not recommended for most use-cases. The APIs
   for writing a custom notary may change in the future.

The first step is to create a service class in your CorDapp that extends the ``NotaryService`` abstract class.
This will ensure that it is recognised as a notary service.
The custom notary service class should provide a constructor with two parameters of types ``ServiceHubInternal`` and ``PublicKey``.
Note that ``ServiceHubInternal`` does not provide any API stability guarantees.

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
        className : "net.corda.notarydemo.MyCustomValidatingNotaryService" # The fully qualified name of your service class
    }