.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Integration testing
===================

Integration testing involves bringing up nodes locally and testing invariants about them by starting flows and inspecting
their state.

In this tutorial we will bring up three nodes - Alice, Bob and a notary. Alice will issue cash to Bob, then Bob will send
this cash back to Alice. We will see how to test some simple deterministic and nondeterministic invariants in the meantime.

.. note:: This example where Alice is self-issuing cash is purely for demonstration purposes, in reality, cash would be
   issued by a bank and subsequently passed around.

In order to spawn nodes we will use the Driver DSL. This DSL allows one to start up node processes from code. It creates
a local network where all the nodes see each other and provides safe shutting down of nodes in the background.

.. container:: codeset

    .. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/KotlinIntegrationTestingTutorial.kt
        :language: kotlin
        :start-after: START 1
        :end-before: END 1
        :dedent: 8

    .. literalinclude:: example-code/src/integration-test/java/net/corda/docs/JavaIntegrationTestingTutorial.java
        :language: java
        :start-after: START 1
        :end-before: END 1
        :dedent: 8

The above code starts two nodes:

* Alice, configured with an RPC user who has permissions to start the ``CashIssueAndPaymentFlow`` flow on it and query
  Alice's vault.
* Bob, configured with an RPC user who only has permissions to start the ``CashPaymentFlow`` and query Bob's vault.

.. note:: You will notice that we did not start a notary. This is done automatically for us by the driver - it creates
   a notary node with the name ``DUMMY_NOTARY_NAME`` which is visible to both nodes. If you wish to customise this, for
   example create more notaries, then specify the ``DriverParameters.notarySpecs`` parameter.

The ``startNode`` function returns a ``CordaFuture`` object that completes once the node is fully started and visible on
the local network. Returning a future allows starting of the nodes to be parallel. We wait on these futures as we need
the information returned; their respective ``NodeHandles`` s.

.. container:: codeset

    .. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/KotlinIntegrationTestingTutorial.kt
        :language: kotlin
        :start-after: START 2
        :end-before: END 2
        :dedent: 12

    .. literalinclude:: example-code/src/integration-test/java/net/corda/docs/JavaIntegrationTestingTutorial.java
        :language: java
        :start-after: START 2
        :end-before: END 2
        :dedent: 16

Next we connect to Alice and Bob from the test process using the test users we created. We establish RPC links that allow
us to start flows and query state.

.. container:: codeset

    .. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/KotlinIntegrationTestingTutorial.kt
        :language: kotlin
        :start-after: START 3
        :end-before: END 3
        :dedent: 12

    .. literalinclude:: example-code/src/integration-test/java/net/corda/docs/JavaIntegrationTestingTutorial.java
        :language: java
        :start-after: START 3
        :end-before: END 3
        :dedent: 16

We will be interested in changes to Alice's and Bob's vault, so we query a stream of vault updates from each.

Now that we're all set up we can finally get some cash action going!

.. container:: codeset

    .. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/KotlinIntegrationTestingTutorial.kt
        :language: kotlin
        :start-after: START 4
        :end-before: END 4
        :dedent: 12

    .. literalinclude:: example-code/src/integration-test/java/net/corda/docs/JavaIntegrationTestingTutorial.java
        :language: java
        :start-after: START 4
        :end-before: END 4
        :dedent: 16

We start a ``CashIssueAndPaymentFlow`` flow on the Alice node. We specify that we want Alice to self-issue $1000 which is
to be payed to Bob. We specify the default notary identity created by the driver as the notary responsible for notarising
the created states. Note that no notarisation will occur yet as we're not spending any states, only creating new ones on
the ledger.

We expect a single update to Bob's vault when it receives the $1000 from Alice. This is what the ``expectEvents`` call
is asserting.

.. container:: codeset

    .. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/KotlinIntegrationTestingTutorial.kt
        :language: kotlin
        :start-after: START 5
        :end-before: END 5
        :dedent: 12

    .. literalinclude:: example-code/src/integration-test/java/net/corda/docs/JavaIntegrationTestingTutorial.java
        :language: java
        :start-after: START 5
        :end-before: END 5
        :dedent: 16

Next we want Bob to send this cash back to Alice.

That's it! We saw how to start up several corda nodes locally, how to connect to them, and how to test some simple invariants
about ``CashIssueAndPaymentFlow`` and ``CashPaymentFlow``.

You can find the complete test at ``example-code/src/integration-test/java/net/corda/docs/JavaIntegrationTestingTutorial.java``
(Java) and ``example-code/src/integration-test/kotlin/net/corda/docs/KotlinIntegrationTestingTutorial.kt`` (Kotlin) in the
`Corda repo <https://github.com/corda/corda>`_.
