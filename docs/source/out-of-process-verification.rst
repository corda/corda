Out-of-process verification
===========================

A Corda node does transaction verification through ``ServiceHub.transactionVerifierService``. This is by default an
``InMemoryTransactionVerifierService`` which just verifies transactions in-process.

Corda may be configured to use out of process verification. Any number of verifiers may be started connecting to a node
through the node's exposed artemis SSL port. The messaging layer takes care of load balancing.

.. note:: We plan to introduce kernel level sandboxing around the out of process verifiers as an additional line of
 defence in case of inner sandbox escapes.

To configure a node to use out of process verification specify the ``verifierType`` option in your node.conf:

.. literalinclude:: example-code/src/main/resources/example-out-of-process-verifier-node.conf
    :language: cfg

You can build a verifier jar using ``./gradlew verifier:standaloneJar``.

And run it with ``java -jar verifier/build/libs/corda-verifier.jar <PATH_TO_VERIFIER_BASE_DIR>``.

``PATH_TO_VERIFIER_BASE_DIR`` should contain a ``certificates`` folder akin to the one in a node directory, and a
``verifier.conf`` containing the following:

.. literalinclude:: example-code/src/main/resources/example-verifier.conf
    :language: cfg
