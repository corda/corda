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

Flow testing can be fully automated using a ``MockNetwork`` composed of ``MockNode`` nodes. Each ``MockNode`` behaves
like a regular Corda node, but its services are either in-memory or mocked out.

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
as a CorDapp on each ``MockNode``.

Configuring the ``MockNetwork``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``MockNetwork`` is configured automatically. You can tweak its configuration using a ``MockNetworkParameters``
object, or by using named paramters in Kotlin:

.. container:: codeset

   .. sourcecode:: kotlin

        val network = MockNetwork(
                cordappPackages = listOf("my.cordapp.package", "my.other.cordapp.package"),
                // Whether to run all nodes on a single thread. Allows a debugger to be attached
                // to the nodes, and removes multi-threading as a source of test failures.
                threadPerNode = false,
                // Which notaries to create on the network.
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)),
                // In bytes.
                maxTransactionSize = Int.MAX_VALUE,
                // Whether to manually control the sending of messages between nodes. Allows
                // debugging at the level of individual messages.
                networkSendManuallyPumped = false,
                // How to decide which peer to send a message to when contacting a distributed service.
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
                // Should be left as ``true`` to use the default serialisation environment.
                initialiseSerialization = true)

        val network2 = MockNetwork(listOf("my.cordapp.package", "my.other.cordapp.package"), MockNetworkParameters(
                // Whether to run all nodes on a single thread. Allows a debugger to be attached
                // to the nodes, and removes multi-threading as a source of test failures.
                threadPerNode = false,
                // Which notaries to create on the network.
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)),
                // In bytes.
                maxTransactionSize = Int.MAX_VALUE,
                // Whether to manually control the sending of messages between nodes. Allows
                // debugging at the level of individual messages.
                networkSendManuallyPumped = false,
                // How to decide which peer to send a message to when contacting a distributed service.
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
                // Should be left as ``true`` to use the default serialisation environment.
                initialiseSerialization = true)
        )

   .. sourcecode:: java

        MockNetwork network = MockNetwork(ImmutableList.of("my.cordapp.package", "my.other.cordapp.package"),
                new MockNetworkParameters()
                        // Whether to run all nodes on a single thread. Allows a debugger to be attached
                        // to the nodes, and removes multi-threading as a source of test failures.
                        .setThreadPerNode(false)
                        // Which notaries to create on the network.
                        .setNotarySpecs(ImmutableList.of(new MockNetworkNotarySpec(DUMMY_NOTARY_NAME)))
                        // In bytes.
                        .setMaxTransactionSize(Integer.MAX_VALUE)
                        // Whether to manually control the sending of messages between nodes. Allows
                        // debugging at the level of individual messages.
                        .setNetworkSendManuallyPumped(false)
                        // How to decide which peer to send a message to when contacting a distributed service.
                        .setServicePeerAllocationStrategy(new InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random())
                        // Should be left as ``true`` to use the default serialisation environment.
                        .setInitialiseSerialization(true));

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
``MockNetwork``, each ``MockNode`` must manually register any responder flows it wishes to use.

Responder flows are registered as follows:

.. container:: codeset

   .. sourcecode:: kotlin

        nodeA.registerInitiatedFlow(ExampleFlow.Acceptor::class.java)

   .. sourcecode:: java

        nodeA.registerInitiatedFlow(ExampleFlow.Acceptor.class);

Running the network
^^^^^^^^^^^^^^^^^^^

Regular Corda nodes automatically send and receive messages. When using a ``MockNetwork``, you must manually initiate
the sending and receiving of messages (e.g. after starting a flow).

How the exchange of messages is initiated depends on how the ``MockNetwork`` is configured:

* Using ``MockNetwork.runNetwork`` if ``MockNetwork.networkSendManuallyPumped`` is set to false
    * ``network.runNetwork(-1)`` (the default in Kotlin) will exchange messages until there are no further messages to
      process
* Using ``MockNetwork.pumpReceive`` if ``MockNetwork.networkSendManuallyPumped`` is set to true

Running flows
^^^^^^^^^^^^^

A ``MockNode`` starts a flow using the ``StartedNodeServices.startFlow`` method. This method returns a future
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

Accessing ``MockNode`` internals
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Examining a node's transaction storage
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Recorded transactions can be retrieved from the transaction storage of a ``MockNode`` using:

.. container:: codeset

   .. sourcecode:: kotlin

        val transaction = nodeA.services.validatedTransactions.getTransaction(transaction.id)

   .. sourcecode:: java

        SignedTransaction transaction = nodeA.getServices().getValidatedTransactions().getTransaction(transaction.getId())

This allows you to check whether a given transaction has (or has not) been stored, and whether it has the correct
attributes.

Querying a node's vault
~~~~~~~~~~~~~~~~~~~~~~~

Recorded states can be retrieved from the vault of a ``MockNode`` using:

.. container:: codeset

   .. sourcecode:: kotlin

        val myStates = nodeA.services.vaultService.queryBy<MyStateType>().states

   .. sourcecode:: java

        List<MyStateType> myStates = node.getServices().getVaultService().queryBy(MyStateType.class).getStates();

This allows you to check whether a given state has (or has not) been stored, and whether it has the correct attributes.