.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

API: Testing
============

.. contents::

Flow testing
------------

MockNetwork
^^^^^^^^^^^

Flow testing can be fully automated using a ``MockNetwork`` composed of ``StartedMockNode`` nodes. Each
``StartedMockNode`` behaves like a regular Corda node, but its services are either in-memory or mocked out.

A ``MockNetwork`` is created as follows:

.. container:: codeset

   .. sourcecode:: kotlin

        class FlowTests {
            private lateinit var mockNet: MockNetwork

            @Before
            fun setup() {
                network = MockNetwork(listOf("my.cordapp.package", "my.other.cordapp.package"))
            }
        }


   .. sourcecode:: java

        public class IOUFlowTests {
            private MockNetwork network;

            @Before
            public void setup() {
                network = new MockNetwork(ImmutableList.of("my.cordapp.package", "my.other.cordapp.package"));
            }
        }

The ``MockNetwork`` requires at a minimum a list of packages. Each package is packaged into a CorDapp JAR and installed
as a CorDapp on each ``StartedMockNode``.

Configuring the ``MockNetwork``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``MockNetwork`` is configured automatically. You can tweak its configuration using a ``MockNetworkParameters``
object, or by using named paramters in Kotlin:

.. container:: codeset

   .. sourcecode:: kotlin

        val network = MockNetwork(
                cordappPackages = listOf("my.cordapp.package", "my.other.cordapp.package"),
                // If true then each node will be run in its own thread. This can result in race conditions in your
                // code if not carefully written, but is more realistic and may help if you have flows in your app that
                // do long blocking operations.
                threadPerNode = false,
                // The notaries to use on the mock network. By default you get one mock notary and that is usually
                // sufficient.
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)),
                // If true then messages will not be routed from sender to receiver until you use the
                // [MockNetwork.runNetwork] method. This is useful for writing single-threaded unit test code that can
                // examine the state of the mock network before and after a message is sent, without races and without
                // the receiving node immediately sending a response.
                networkSendManuallyPumped = false,
                // How traffic is allocated in the case where multiple nodes share a single identity, which happens for
                // notaries in a cluster. You don't normally ever need to change this: it is mostly useful for testing
                // notary implementations.
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random())

        val network2 = MockNetwork(listOf("my.cordapp.package", "my.other.cordapp.package"), MockNetworkParameters(
                // If true then each node will be run in its own thread. This can result in race conditions in your
                // code if not carefully written, but is more realistic and may help if you have flows in your app that
                // do long blocking operations.
                threadPerNode = false,
                // The notaries to use on the mock network. By default you get one mock notary and that is usually
                // sufficient.
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)),
                // If true then messages will not be routed from sender to receiver until you use the
                // [MockNetwork.runNetwork] method. This is useful for writing single-threaded unit test code that can
                // examine the state of the mock network before and after a message is sent, without races and without
                // the receiving node immediately sending a response.
                networkSendManuallyPumped = false,
                // How traffic is allocated in the case where multiple nodes share a single identity, which happens for
                // notaries in a cluster. You don't normally ever need to change this: it is mostly useful for testing
                // notary implementations.
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random())
        )

   .. sourcecode:: java

        MockNetwork network = MockNetwork(ImmutableList.of("my.cordapp.package", "my.other.cordapp.package"),
                new MockNetworkParameters()
                        // If true then each node will be run in its own thread. This can result in race conditions in
                        // your code if not carefully written, but is more realistic and may help if you have flows in
                        // your app that do long blocking operations.
                        .setThreadPerNode(false)
                        // The notaries to use on the mock network. By default you get one mock notary and that is
                        // usually sufficient.
                        .setNotarySpecs(ImmutableList.of(new MockNetworkNotarySpec(DUMMY_NOTARY_NAME)))
                        // If true then messages will not be routed from sender to receiver until you use the
                        // [MockNetwork.runNetwork] method. This is useful for writing single-threaded unit test code
                        // that can examine the state of the mock network before and after a message is sent, without
                        // races and without the receiving node immediately sending a response.
                        .setNetworkSendManuallyPumped(false)
                        // How traffic is allocated in the case where multiple nodes share a single identity, which
                        // happens for notaries in a cluster. You don't normally ever need to change this: it is mostly
                        // useful for testing notary implementations.
                        .setServicePeerAllocationStrategy(new InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random()));

Adding nodes to the network
^^^^^^^^^^^^^^^^^^^^^^^^^^^

Nodes are created on the ``MockNetwork`` using:

.. container:: codeset

   .. sourcecode:: kotlin

        class FlowTests {
            private lateinit var mockNet: MockNetwork
            lateinit var nodeA: StartedMockNode
            lateinit var nodeB: StartedMockNode

            @Before
            fun setup() {
                network = MockNetwork(listOf("my.cordapp.package", "my.other.cordapp.package"))
                nodeA = network.createPartyNode()
                // We can optionally give the node a name.
                nodeB = network.createPartyNode(CordaX500Name("Bank B", "London", "GB"))
            }
        }


   .. sourcecode:: java

        public class IOUFlowTests {
            private MockNetwork network;
            private StartedMockNode a;
            private StartedMockNode b;

            @Before
            public void setup() {
                network = new MockNetwork(ImmutableList.of("my.cordapp.package", "my.other.cordapp.package"));
                nodeA = network.createPartyNode(null);
                // We can optionally give the node a name.
                nodeB = network.createPartyNode(new CordaX500Name("Bank B", "London", "GB"));
            }
        }

Registering a node's initiated flows
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Regular Corda nodes automatically register any response flows defined in their installed CorDapps. When using a
``MockNetwork``, each ``StartedMockNode`` must manually register any responder flows it wishes to use.

Responder flows are registered as follows:

.. container:: codeset

   .. sourcecode:: kotlin

        nodeA.registerInitiatedFlow(ExampleFlow.Acceptor::class.java)

   .. sourcecode:: java

        nodeA.registerInitiatedFlow(ExampleFlow.Acceptor.class);

Running the network
^^^^^^^^^^^^^^^^^^^

Regular Corda nodes automatically process received messages. When using a ``MockNetwork`` with
``networkSendManuallyPumped`` set to ``false``, you must manually initiate the processing of received messages.

You manually process received messages as follows:

* ``StartedMockNode.pumpReceive`` to process a single message from the node's queue

* ``MockNetwork.runNetwork`` to process all the messages in every node's queue. This may generate additional messages
  that must in turn be processed

    * ``network.runNetwork(-1)`` (the default in Kotlin) will exchange messages until there are no further messages to
      process

Running flows
^^^^^^^^^^^^^

A ``StartedMockNode`` starts a flow using the ``StartedNodeServices.startFlow`` method. This method returns a future
representing the output of running the flow.

.. container:: codeset

   .. sourcecode:: kotlin

        val signedTransactionFuture = nodeA.services.startFlow(IOUFlow(iouValue = 99, otherParty = nodeBParty))

   .. sourcecode:: java

        CordaFuture<SignedTransaction> future = startFlow(a.getServices(), new ExampleFlow.Initiator(1, nodeBParty));

The network must then be manually run before retrieving the future's value:

.. container:: codeset

   .. sourcecode:: kotlin

        val signedTransactionFuture = nodeA.services.startFlow(IOUFlow(iouValue = 99, otherParty = nodeBParty))
        // Assuming network.networkSendManuallyPumped == false.
        network.runNetwork()
        val signedTransaction = future.get();

   .. sourcecode:: java

        CordaFuture<SignedTransaction> future = startFlow(a.getServices(), new ExampleFlow.Initiator(1, nodeBParty));
        // Assuming network.networkSendManuallyPumped == false.
        network.runNetwork();
        SignedTransaction signedTransaction = future.get();

Accessing ``StartedMockNode`` internals
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Creating a node database transaction
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Whenever you query a node's database (e.g. to extract information from the node's vault), you must wrap the query in
a database transaction, as follows:

.. container:: codeset

   .. sourcecode:: kotlin

        nodeA.database.transaction {
            // Perform query here.
        }

   .. sourcecode:: java

        node.getDatabase().transaction(tx -> {
            // Perform query here.
        }

Querying a node's vault
~~~~~~~~~~~~~~~~~~~~~~~

Recorded states can be retrieved from the vault of a ``StartedMockNode`` using:

.. container:: codeset

   .. sourcecode:: kotlin

        nodeA.database.transaction {
            val myStates = nodeA.services.vaultService.queryBy<MyStateType>().states
        }

   .. sourcecode:: java

        node.getDatabase().transaction(tx -> {
            List<MyStateType> myStates = node.getServices().getVaultService().queryBy(MyStateType.class).getStates();
        }

This allows you to check whether a given state has (or has not) been stored, and whether it has the correct attributes.

Examining a node's transaction storage
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Recorded transactions can be retrieved from the transaction storage of a ``StartedMockNode`` using:

.. container:: codeset

   .. sourcecode:: kotlin

        val transaction = nodeA.services.validatedTransactions.getTransaction(transaction.id)

   .. sourcecode:: java

        SignedTransaction transaction = nodeA.getServices().getValidatedTransactions().getTransaction(transaction.getId())

This allows you to check whether a given transaction has (or has not) been stored, and whether it has the correct
attributes.