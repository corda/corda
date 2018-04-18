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
                // A list of packages to scan. Any contracts, flows and Corda services within these
                // packages will be automatically available to any nodes within the mock network
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

        val network2 = MockNetwork(
                // A list of packages to scan. Any contracts, flows and Corda services within these
                // packages will be automatically available to any nodes within the mock network
                listOf("my.cordapp.package", "my.other.cordapp.package"), MockNetworkParameters(
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

        MockNetwork network = MockNetwork(
                // A list of packages to scan. Any contracts, flows and Corda services within these
                // packages will be automatically available to any nodes within the mock network
                ImmutableList.of("my.cordapp.package", "my.other.cordapp.package"),
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

This allows you to check whether a given state has (or has not) been stored, and whether it has the correct attributes.

Further examples
^^^^^^^^^^^^^^^^

* See the flow testing tutorial :doc:`here <flow-testing>`
* See the oracle tutorial :doc:`here <oracles>` for information on testing ``@CordaService`` classes
* Further examples are available in the Example CorDapp in
  `Java <https://github.com/corda/cordapp-example/blob/release-V3/java-source/src/test/java/com/example/flow/IOUFlowTests.java>`_ and
  `Kotlin <https://github.com/corda/cordapp-example/blob/release-V3/kotlin-source/src/test/kotlin/com/example/flow/IOUFlowTests.kt>`_

Contract testing
----------------

The Corda test framework includes the ability to create a test ledger by calling the ``ledger`` function
on an implementation of the ``ServiceHub`` interface.

Test identities
^^^^^^^^^^^^^^^

You can create dummy identities to use in test transactions using the ``TestIdentity`` class:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 14
        :end-before: DOCEND 14
        :dedent: 8

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 14
        :end-before: DOCEND 14
        :dedent: 4

``TestIdentity`` exposes the following fields and methods:

.. container:: codeset

   .. sourcecode:: kotlin

        val identityParty: Party = bigCorp.party
        val identityName: CordaX500Name = bigCorp.name
        val identityPubKey: PublicKey = bigCorp.publicKey
        val identityKeyPair: KeyPair = bigCorp.keyPair
        val identityPartyAndCertificate: PartyAndCertificate = bigCorp.identity

   .. sourcecode:: java

        Party identityParty = bigCorp.getParty();
        CordaX500Name identityName = bigCorp.getName();
        PublicKey identityPubKey = bigCorp.getPublicKey();
        KeyPair identityKeyPair = bigCorp.getKeyPair();
        PartyAndCertificate identityPartyAndCertificate = bigCorp.getIdentity();

You can also create a unique ``TestIdentity`` using the ``fresh`` method:

.. container:: codeset

   .. sourcecode:: kotlin

        val uniqueTestIdentity: TestIdentity = TestIdentity.fresh("orgName")

   .. sourcecode:: java

        TestIdentity uniqueTestIdentity = TestIdentity.Companion.fresh("orgName");

MockServices
^^^^^^^^^^^^

A mock implementation of ``ServiceHub`` is provided in ``MockServices``. This is a minimal ``ServiceHub`` that
suffices to test contract logic. It has the ability to insert states into the vault, query the vault, and
construct and check transactions.

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 11
        :end-before: DOCEND 11
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 11
        :end-before: DOCEND 11
        :dedent: 4


Alternatively, there is a helper constructor which just accepts a list of ``TestIdentity``. The first identity provided is
the identity of the node whose ``ServiceHub`` is being mocked, and any subsequent identities are identities that the node
knows about. Only the calling package is scanned for cordapps and a test ``IdentityService`` is created
for you, using all the given identities.

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 12
        :end-before: DOCEND 12
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 12
        :end-before: DOCEND 12
        :dedent: 4


Writing tests using a test ledger
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The ``ServiceHub.ledger`` extension function allows you to create a test ledger. Within the ledger wrapper you can create
transactions using the ``transaction`` function. Within a transaction you can define the ``input`` and
``output`` states for the transaction, alongside any commands that are being executed, the ``timeWindow`` in which the
transaction has been executed, and any ``attachments``, as shown in this example test:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 13
        :end-before: DOCEND 13
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 13
        :end-before: DOCEND 13
        :dedent: 4

Once all the transaction components have been specified, you can run ``verifies()`` to check that the given transaction is valid.

Checking for failure states
~~~~~~~~~~~~~~~~~~~~~~~~~~~

In order to test for failures, you can use the ``failsWith`` method, or in Kotlin the ``fails with`` helper method, which
assert that the transaction fails with a specific error. If you just want to assert that the transaction has failed without
verifying the message, there is also a ``fails`` method.

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 4
        :end-before: DOCEND 4
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 4
        :end-before: DOCEND 4
        :dedent: 4

.. note::

    The transaction DSL forces the last line of the test to be either a ``verifies`` or ``fails with`` statement.

Testing multiple scenarios at once
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Within a single transaction block, you can assert several times that the transaction constructed so far either passes or
fails verification. For example, you could test that a contract fails to verify because it has no output states, and then
add the relevant output state and check that the contract verifies successfully, as in the following example:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 5
        :end-before: DOCEND 5
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 5
        :end-before: DOCEND 5
        :dedent: 4

You can also use the ``tweak`` function to create a locally scoped transaction that you can make changes to
and then return to the original, unmodified transaction. As in the following example:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 7
        :end-before: DOCEND 7
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 7
        :end-before: DOCEND 7
        :dedent: 4


Chaining transactions
~~~~~~~~~~~~~~~~~~~~~

The following example shows that within a ``ledger``, you can create more than one ``transaction`` in order to test chains
of transactions. In addition to ``transaction``, ``unverifiedTransaction`` can be used, as in the example below, to create
transactions on the ledger without verifying them, for pre-populating the ledger with existing data. When chaining transactions,
it is important to note that even though a ``transaction`` ``verifies`` successfully, the overall ledger may not be valid. This can
be verified separately by placing a ``verifies`` or ``fails`` statement  within the ``ledger`` block.

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/test/kotlin/net/corda/docs/tutorial/testdsl/TutorialTestDSL.kt
        :language: kotlin
        :start-after: DOCSTART 9
        :end-before: DOCEND 9
        :dedent: 4

    .. literalinclude:: ../../docs/source/example-code/src/test/java/net/corda/docs/java/tutorial/testdsl/CommercialPaperTest.java
        :language: java
        :start-after: DOCSTART 9
        :end-before: DOCEND 9
        :dedent: 4


Further examples
^^^^^^^^^^^^^^^^

* See the flow testing tutorial :doc:`here <tutorial-test-dsl>`
* Further examples are available in the Example CorDapp in
  `Java <https://github.com/corda/cordapp-example/blob/release-V3/java-source/src/test/java/com/example/flow/IOUFlowTests.java>`_ and
  `Kotlin <https://github.com/corda/cordapp-example/blob/release-V3/kotlin-source/src/test/kotlin/com/example/flow/IOUFlowTests.kt>`_
