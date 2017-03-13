.. _graphstream: http://graphstream-project.org/

Client RPC API tutorial
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

.. warning:: This API is evolving and will continue to grow as new functionality and features added to Corda are made available to RPC clients.

The one we need in order to dump the transaction graph is ``verifiedTransactions``. The type signature tells us that the
RPC will return a list of transactions and an Observable stream. This is a general pattern, we query some data and the
node will return the current snapshot and future updates done to it. Observables are described in further detail in
:doc:`clientrpc`

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

.. code-block:: text

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

Whitelisting classes from your CorDapp with the Corda node
----------------------------------------------------------

As described in :doc:`clientrpc`, you have to whitelist any additional classes you add that are needed in RPC
requests or responses with the Corda node.  Here's an example of both ways you can do this for a couple of example classes.

.. literalinclude:: example-code/src/main/kotlin/net/corda/docs/ClientRpcTutorial.kt
    :language: kotlin
    :start-after: START 7
    :end-before: END 7

See more on plugins in :doc:`creating-a-cordapp`.

.. warning:: We will be replacing the use of Kryo in the serialization framework and so additional changes here are likely.

Security
--------
RPC credentials associated with a Client must match the permission set configured on the server Node.
This refers to both authentication (username and password) and role-based authorisation (a permissioned set of RPC operations an
authenticated user is entitled to run).

.. note:: Permissions are represented as *String's* to allow RPC implementations to add their own permissioning.
     Currently the only permission type defined is *StartFlow*, which defines a list of whitelisted flows an authenticated use may execute.

In the instructions above the server node permissions are configured programmatically in the driver code:

.. code-block:: text

        driver(driverDirectory = baseDirectory) {
            val user = User("user", "password", permissions = setOf(startFlowPermission<CashFlow>()))
            val node = startNode("Alice", rpcUsers = listOf(user)).get()

When starting a standalone node using a configuration file we must supply the RPC credentials as follows:

.. code-block:: text

    rpcUsers : [
        { user=user, password=password, permissions=[ StartFlow.net.corda.flows.CashFlow ] }
    ]

When using the gradle Cordformation plugin to configure and deploy a node you must supply the RPC credentials in a similar manner:

.. code-block:: text

        rpcUsers = [
                ['user' : "user",
                 'password' : "password",
                 'permissions' : ["StartFlow.net.corda.flows.CashFlow"]]
        ]

You can then deploy and launch the nodes (Notary and Alice) as follows:

.. code-block:: text

    # to create a set of configs and installs under ``docs/source/example-code/build/nodes`` run
    ./gradlew docs/source/example-code:deployNodes
    # to open up two new terminals with the two nodes run
    ./docs/source/example-code/build/nodes/runnodes
    # followed by the same commands as before:
    ./docs/source/example-code/build/install/docs/source/example-code/bin/client-rpc-tutorial Print
    ./docs/source/example-code/build/install/docs/source/example-code/bin/client-rpc-tutorial Visualise

See more on security in :doc:`secure-coding-guidelines`,  node configuration in :doc:`corda-configuration-file` and
Cordformation in :doc:`creating-a-cordapp`
