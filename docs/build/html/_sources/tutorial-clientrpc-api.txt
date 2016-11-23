.. _graphstream: http://graphstream-project.org/

Client RPC API Tutorial
=======================

In this tutorial we will build a simple command line utility that
connects to a node, creates some Cash transactions and meanwhile dumps
the transaction graph to the standard output. We will then put some
simple visualisation on top. For an explanation on how the RPC works
see :doc:`clientrpc`.

We start off by connecting to the node itself. For the purposes of the tutorial we will use the Driver to start up a notary and a node that issues/exits and moves Cash around for herself. To authenticate we will use the certificates of the nodes directly.

Note how we configure the node to create a user that has permission to start the CashFlow.

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 1
    :end-before: END 1

Now we can connect to the node itself using a valid RPC login. We login using the configured user.

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 2
    :end-before: END 2

We start generating transactions in a different thread (``generateTransactions`` to be defined later) using ``proxy``, which exposes the full RPC interface of the node:

.. literalinclude:: ../../node/src/main/kotlin/net/corda/node/services/messaging/CordaRPCOps.kt
    :language: kotlin
    :start-after: interface CordaRPCOps
    :end-before: }

The one we need in order to dump the transaction graph is ``verifiedTransactions``. The type signature tells us that the
RPC will return a list of transactions and an Observable stream. This is a general pattern, we query some data and the
node will return the current snapshot and future updates done to it.

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 3
    :end-before: END 3

The graph will be defined by nodes and edges between them. Each node represents a transaction and edges represent
output-input relations. For now let's just print ``NODE <txhash>`` for the former and ``EDGE <txhash> <txhash>`` for the
latter.

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 4
    :end-before: END 4


Now we just need to create the transactions themselves!

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 6
    :end-before: END 6

We utilise several RPC functions here to query things like the notaries in the node cluster or our own vault.

Then in a loop we generate randomly either an Issue, a Pay or an Exit transaction.

The RPC we need to initiate a Cash transaction is ``startFlowDynamic`` which may start an arbitrary flow, given sufficient permissions to do so. We won't use this function directly, but rather a type-safe wrapper around it ``startFlow`` that type-checks the arguments for us.

Finally we have everything in place: we start a couple of nodes, connect to them, and start creating transactions while listening on successfully created ones, which are dumped to the console. We just need to run it!:

    # Build the example
    ./gradlew docs/source/example-code:installDist
    # Start it
    ./docs/source/example-code/build/install/docs/source/example-code/bin/client-rpc-tutorial Print

Now let's try to visualise the transaction graph. We will use a graph drawing library called graphstream_

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 5
    :end-before: END 5

If we run the client with ``Visualise`` we should see a simple random graph being drawn as new transactions are being created.

Registering classes from your Cordapp with RPC Kryo
---------------------------------------------------

As described in :doc:`clientrpc`, you currently have to register any additional classes you add that are needed in RPC
requests or responses with the `Kryo` instance RPC uses.  Here's an example of how you do this for an example class.

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 7
    :end-before: END 7

See more on plugins in :doc:`creating-a-cordapp`.

.. warning:: We will be replacing the use of Kryo in RPC with a stable message format and this will mean that this plugin
    customisation point will either go away completely or change.