.. highlight:: kotlin

Writing a custom notary service (experimental)
==============================================

.. warning:: Customising a notary service is still an experimental feature and not recommended for most use-cases. The APIs
   for writing a custom notary may change in the future. Additionally, customising Raft or BFT notaries is not yet
   fully supported. If you want to write your own Raft notary you will have to implement a custom database connector
   (or use a separate database for the notary), and use a custom configuration file.

Similarly to writing an oracle service, the first step is to create a service class in your CorDapp and annotate it
with ``@CordaService``. The Corda node scans for any class with this annotation and initialises them. The custom notary
service class should provide a constructor with two parameters of types ``AppServiceHub`` and ``PublicKey``.

.. literalinclude:: ../../samples/notary-demo/src/main/kotlin/net/corda/notarydemo/MyCustomNotaryService.kt
   :language: kotlin
   :start-after: START 1
   :end-before: END 1

The next step is to write a notary service flow. You are free to copy and modify the existing built-in flows such
as ``ValidatingNotaryFlow``, ``NonValidatingNotaryFlow``, or implement your own from scratch (following the
``NotaryFlow.Service`` template). Below is an example of a custom flow for a *validating* notary service:

.. literalinclude:: ../../samples/notary-demo/src/main/kotlin/net/corda/notarydemo/MyCustomNotaryService.kt
   :language: kotlin
   :start-after: START 2
   :end-before: END 2

To enable the service, add the following to the node configuration:

.. parsed-literal::

    notary : {
        validating : true # Set to false if your service is non-validating
        custom : true
    }