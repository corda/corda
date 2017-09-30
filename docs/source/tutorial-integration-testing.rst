Integration testing
===================

Integration testing involves bringing up nodes locally and testing
invariants about them by starting flows and inspecting their state.

In this tutorial we will bring up three nodes - Alice, Bob and a
notary. Alice will issue cash to Bob, then Bob will send this cash
back to Alice. We will see how to test some simple deterministic and
nondeterministic invariants in the meantime.

.. note:: This example where Alice is self-issuing cash is purely for
   demonstration purposes, in reality, cash would be issued by a bank
   and subsequently passed around.

In order to spawn nodes we will use the Driver DSL. This DSL allows
one to start up node processes from code. It manages a network map
service and safe shutting down of nodes in the background.

.. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/IntegrationTestingTutorial.kt
    :language: kotlin
    :start-after: START 1
    :end-before: END 1
    :dedent: 8

The above code starts three nodes:

* Alice, who has user permissions to start the ``CashIssueFlow`` and
  ``CashPaymentFlow`` flows
* Bob, who only has user permissions to start the ``CashPaymentFlow``
* A notary that offers a ``ValidatingNotaryService``. We won't connect
  to the notary directly, so there's no need to provide a ``User``

The ``startNode`` function returns a future that completes once the
node is fully started. This allows starting of the nodes to be
parallel. We wait on these futures as we need the information
returned; their respective ``NodeHandles`` s.

.. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/IntegrationTestingTutorial.kt
    :language: kotlin
    :start-after: START 2
    :end-before: END 2
    :dedent: 12

After getting the handles we wait for both parties to register with
the network map to ensure we don't have race conditions with network
map registration. Next we connect to Alice and Bob respectively from
the test process using the test user we created. Then we establish RPC
links that allow us to start flows and query state.

.. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/IntegrationTestingTutorial.kt
    :language: kotlin
    :start-after: START 3
    :end-before: END 3
    :dedent: 12

We will be interested in changes to Alice's and Bob's vault, so we
query a stream of vault updates from each.

Now that we're all set up we can finally get some cash action going!

.. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/IntegrationTestingTutorial.kt
    :language: kotlin
    :start-after: START 4
    :end-before: END 4
    :dedent: 12

The first loop creates 10 threads, each starting a ``CashFlow`` flow
on the Alice node. We specify that we want to issue ``i`` dollars to
Bob, setting our notary as the notary responsible for notarising the
created states. Note that no notarisation will occur yet as we're not
spending any states, only creating new ones on the ledger.

We started the flows from different threads for the sake of the
tutorial, to demonstrate how to test non-determinism, which is what
the ``expectEvents`` block does.

The Expect DSL allows ordering constraints to be checked on a stream
of events. The above code specifies that we are expecting 10 updates
to be emitted on the ``bobVaultUpdates`` stream in unspecified order
(this is what the ``parallel`` construct does). We specify an
(otherwise optional) ``match`` predicate to identify specific updates
we are interested in, which we then print.

If we run the code written so far we should see 4 nodes starting up
(Alice, Bob, the notary and an implicit Network Map service), then
10 logs of Bob receiving 1,2,...10 dollars from Alice in some unspecified
order.

Next we want Bob to send this cash back to Alice.

.. literalinclude:: example-code/src/integration-test/kotlin/net/corda/docs/IntegrationTestingTutorial.kt
    :language: kotlin
    :start-after: START 5
    :end-before: END 5
    :dedent: 12

This time we'll do it sequentially. We make Bob pay 1,2,..10 dollars
to Alice in order. We make sure that a the ``CashFlow`` has finished
by waiting on ``startFlow`` 's ``returnValue``.

Then we use the Expect DSL again, this time using ``sequence`` to test
for the updates arriving in the order we expect them to.

Note that ``parallel`` and ``sequence`` may be nested into each other
arbitrarily to test more complex scenarios.

That's it! We saw how to start up several corda nodes locally, how to
connect to them, and how to test some simple invariants about
``CashFlow``.

To run the complete test you can open
``example-code/src/integration-test/kotlin/net/corda/docs/IntegrationTestingTutorial.kt``
from IntelliJ and run the test, or alternatively use gradle:

.. sourcecode:: bash

     # Run example-code integration tests
     ./gradlew docs/source/example-code:integrationTest -i