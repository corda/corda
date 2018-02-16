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

You create a ``MockNetwork`` as follows:

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

The ``MockNetwork`` is configured using a ``MockNetworkParameters`` object, or by using named paramters in Kotlin:

.. container:: codeset

   .. sourcecode:: kotlin

        val network = MockNetwork(
                cordappPackages = listOf("my.cordapp.package", "my.other.cordapp.package"),
                // Whether to run all nodes on a single thread to improve speed.
                threadPerNode = false,
                // Which notaries to create on the network.
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)),
                // In bytes.
                maxTransactionSize = Int.MAX_VALUE,
                // Whether to manually control the sending of messages between nodes.
                networkSendManuallyPumped = false,
                // How to decide whether peer to send a message to when contacting a distributed service.
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
                // Whether to create a serialisation environment.
                initialiseSerialization = true)

        val network2 = MockNetwork(listOf("my.cordapp.package", "my.other.cordapp.package"), MockNetworkParameters(
                // Whether to run all nodes on a single thread to improve speed.
                threadPerNode = false,
                // Which notaries to create on the network.
                notarySpecs = listOf(MockNetworkNotarySpec(DUMMY_NOTARY_NAME)),
                // In bytes.
                maxTransactionSize = Int.MAX_VALUE,
                // Whether to manually control the sending of messages between nodes.
                networkSendManuallyPumped = false,
                // How to decide whether peer to send a message to when contacting a distributed service.
                servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random(),
                // Whether to create a serialisation environment.
                initialiseSerialization = true)
        )

   .. sourcecode:: java

        MockNetwork network = MockNetwork(ImmutableList.of("my.cordapp.package", "my.other.cordapp.package"),
                new MockNetworkParameters()
                        // Whether to run all nodes on a single thread to improve speed.
                        .setThreadPerNode(false)
                        // Which notaries to create on the network.
                        .setNotarySpecs(ImmutableList.of(new MockNetworkNotarySpec(DUMMY_NOTARY_NAME)))
                        // In bytes.
                        .setMaxTransactionSize(Integer.MAX_VALUE)
                        // Whether to manually control the sending of messages between nodes.
                        .setNetworkSendManuallyPumped(false)
                        // How to decide whether peer to send a message to when contacting a distributed service.
                        .setServicePeerAllocationStrategy(new InMemoryMessagingNetwork.ServicePeerAllocationStrategy.Random())
                        // Whether to create a serialisation environment.
                        .setInitialiseSerialization(true));

Adding nodes to the network
^^^^^^^^^^^^^^^^^^^^^^^^^^^

You creates nodes on the ``MockNetwork`` using:

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

Running the network
^^^^^^^^^^^^^^^^^^^

* Use `MockNetwork.runNetwork` to simulate the sending of messages around the network
* The default value of -1 sends messages around the network until there are no more messages to send

Running flows
^^^^^^^^^^^^^

* Use the `StartedNodeServices.startFlow` extension method to call a flow and get a future representing the flow's output
* You can retrieve the result of the flow from the future for testing
* Ensure you run `MockNetwork.runNetwork` before resolving the future, so that the messages sent as part of the flow are processed

Examples
^^^^^^^^

Checking the nodes' tx storages
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

(see https://github.com/corda/cordapp-example/blob/release-V2/kotlin-source/src/test/kotlin/com/example/flow/IOUFlowTests.kt#L86)

Checking the nodes' vaults
~~~~~~~~~~~~~~~~~~~~~~~~~~

(see https://github.com/corda/cordapp-example/blob/release-V2/kotlin-source/src/test/kotlin/com/example/flow/IOUFlowTests.kt#L107)