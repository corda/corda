.. _graphstream: http://graphstream-project.org/

Client RPC API
==============

In this tutorial we will build a simple command line utility that connects to a node and dumps the transaction graph to
the standard output. We will then put some simple visualisation on top. For an explanation on how the RPC works see
:doc:`clientrpc`.

We start off by connecting to the node itself. For the purposes of the tutorial we will run the Trader demo on some
local port and connect to the Buyer side. We will pass in the address as a command line argument. To connect to the node
we also need to access the certificates of the node, we will access the node's ``certificates`` directory directly.

.. literalinclude:: example-code/src/main/kotlin/com/r3corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 1
    :end-before: END 1

Now we can connect to the node itself:

.. literalinclude:: example-code/src/main/kotlin/com/r3corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 2
    :end-before: END 2

``proxy`` now exposes the full RPC interface of the node:

.. literalinclude:: ../../node/src/main/kotlin/com/r3corda/node/services/messaging/CordaRPCOps.kt
    :language: kotlin
    :start-after: interface CordaRPCOps
    :end-before: }

The one we need in order to dump the transaction graph is ``verifiedTransactions``. The type signature tells us that the
RPC will return a list of transactions and an Observable stream. This is a general pattern, we query some data and the
node will return the current snapshot and future updates done to it.

.. literalinclude:: example-code/src/main/kotlin/com/r3corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 3
    :end-before: END 3

The graph will be defined by nodes and edges between them. Each node represents a transaction and edges represent
output-input relations. For now let's just print ``NODE <txhash>`` for the former and ``EDGE <txhash> <txhash>`` for the
latter.

.. literalinclude:: example-code/src/main/kotlin/com/r3corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 4
    :end-before: END 4

Now we can start the trader demo as per described in :doc:`running-the-demos`::

    # Build the demo
    ./gradlew installDist
    # Start the buyer
    ./build/install/r3prototyping/bin/trader-demo --role=BUYER

In another terminal we can connect to it with our client::

    # Connect to localhost:31337
    ./docs/source/code/build/install/docs/source/code/bin/client-rpc-tutorial localhost:31337 Print

We should see some ``NODE``-s printed. This is because the buyer self-issues some cash for the demo.
Unless we ran the seller before we shouldn't see any ``EDGE``-s because the cash hasn't been spent yet.

In another terminal we can now start the seller::

    # Start sellers in a loop
    for i in {0..9} ; do ./build/install/r3prototyping/bin/trader-demo --role=SELLER ; done

We should start seeing new ``NODE``-s and ``EDGE``-s appearing.

Now let's try to visualise the transaction graph. We will use a graph drawing library called graphstream_

.. literalinclude:: example-code/src/main/kotlin/com/r3corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 5
    :end-before: END 5

If we run the client with ``Visualise`` we should see a simple graph being drawn as new transactions are being created
by the seller runs.

That's it! We saw how to connect to the node and stream data from it.
