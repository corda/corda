.. highlight:: kotlin

Writing a custom notary service (experimental)
==============================================

.. warning:: Customising a notary service is still an experimental feature and not recommended for most use-cases. The APIs
   for writing a custom notary may change in the future.

The first step is to create a service class in your CorDapp that extends the ``NotaryService`` abstract class.
This will ensure that it is recognised as a notary service.
The custom notary service class should provide a constructor with two parameters of types ``ServiceHubInternal`` and ``PublicKey``.
Note that ``ServiceHubInternal`` does not provide any API stability guarantees.

.. literalinclude:: ../../samples/notary-demo/workflows/src/main/kotlin/net/corda/notarydemo/MyCustomNotaryService.kt
   :language: kotlin
   :start-after: START 1
   :end-before: END 1

The next step is to write a notary service flow. You are free to copy and modify the existing built-in flows such
as ``ValidatingNotaryFlow``, ``NonValidatingNotaryFlow``, or implement your own from scratch (following the
``NotaryFlow.Service`` template). Below is an example of a custom flow for a *validating* notary service:

.. literalinclude:: ../../samples/notary-demo/workflows/src/main/kotlin/net/corda/notarydemo/MyCustomNotaryService.kt
   :language: kotlin
   :start-after: START 2
   :end-before: END 2

To enable the service, add the following to the node configuration:

.. code-block:: none

    notary : {
        validating : true # Set to false if your service is non-validating
        className : "net.corda.notarydemo.MyCustomValidatingNotaryService" # The fully qualified name of your service class
    }

Testing your custom notary service
---------------------------------

To create a flow test that uses your custom notary service, you can set the class name of the custom notary service as follows in your flow test:

.. literalinclude:: ../../testing/node-driver/src/test/kotlin/net/corda/testing/node/CustomNotaryTest.kt
   :language: kotlin
   :start-after: START 1
   :end-before: END 1

After this, your custom notary will be the default notary on the mock network, and can be used in the same way as described in :doc:`flow-testing`.

