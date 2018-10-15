.. highlight:: kotlin

Writing a custom notary service (experimental)
==============================================

.. warning:: Customising a notary service is still an experimental feature and not recommended for most use-cases. The APIs
   for writing a custom notary may change in the future.

The first step is to create a service class in your CorDapp that extends the ``NotaryService`` abstract class.
This will ensure that it is recognised as a notary service.
The custom notary service class should provide a constructor with two parameters of types ``ServiceHubInternal`` and ``PublicKey``.
Note that ``ServiceHubInternal`` does not provide any API stability guarantees.

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
        className : "net.corda.notarydemo.MyCustomValidatingNotaryService" # The fully qualified name of your service class
    }