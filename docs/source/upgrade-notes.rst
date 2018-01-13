Upgrading a CorDapp to a new version
====================================

These notes provide instructions for upgrading your CorDapps from previous versions, starting with the upgrade from our
first public Beta (:ref:`Milestone 12 <changelog_m12>`), to :ref:`V1.0 <changelog_v1>`.

.. contents::
   :depth: 3

General rules
-------------
Always remember to update the version identifiers in your project gradle file. For example, Corda V1.0 uses:

.. sourcecode:: shell

    ext.corda_release_version = '1.0.0'
    ext.corda_release_distribution = 'net.corda'
    ext.corda_gradle_plugins_version = '1.0.0'

It may be necessary to update the version of major dependencies. Corda V1.0 uses:

.. sourcecode:: shell

    ext.kotlin_version = '1.1.4'
    ext.quasar_version = '0.7.9'
    ext.junit_version = '4.12'

Please consult the relevant release notes of the release in question. If not specified, you may assume the
versions you are currently using are still in force.

We also strongly recommend cross referencing with the :doc:`changelog` to confirm changes.

V1.0 and V2.0 to R3 Corda V3.0 Developer Preview
------------------------------------------------

Build
^^^^^

* Update the version identifiers in your project gradle file(s):

.. sourcecode:: shell

    ext.corda_release_version = 'R3.CORDA-3.0.0-DEV-PREVIEW'    // "CORDA-3.0.0-DEV-PREVIEW" (Open Source)
    ext.corda_gradle_plugins_version = '3.0.3'

* Add a new release identifier to specify the R3 release of corda:

.. sourcecode:: shell

    ext.corda_release_distribution = 'com.r3.corda'             // "net.corda" for Corda (Open Source)

* Add an additional repository entry to point to the location of the R3 Corda distribution:

.. sourcecode:: shell

    repositories {
        maven {
            credentials {
                username "r3-corda-dev-preview"
                password "XXXXX"
            }
            url 'https://ci-artifactory.corda.r3cev.com/artifactory/r3-corda-releases'
        }
    }

* Corda plugins have been modularised further so the following additional gradle entries are necessary:

.. sourcecode:: shell

    dependencies {
        classpath "net.corda.plugins:cordapp:$corda_gradle_plugins_version"
    }

    apply plugin: 'net.corda.plugins.cordapp'

  The plugin needs to be applied in all gradle build files where there is a dependency on Corda using any of:
  cordaCompile, cordaRuntime, cordapp

* Corda Gradle plugins require Gradle version 4.1 or above

* All gradle compile, test, and run-time dependencies (except gradle plugins) to Corda artifacts should now use the
  ``corda_release_distribution`` variable (was previously hardcoded to use ``net.corda``):

.. sourcecode:: shell

    dependencies {

        // Corda integration dependencies
        cordaCompile "$corda_release_distribution:corda-core:$corda_release_version"
        cordaCompile "$corda_release_distribution:corda-finance:$corda_release_version"
        cordaCompile "$corda_release_distribution:corda-jackson:$corda_release_version"
        cordaCompile "$corda_release_distribution:corda-rpc:$corda_release_version"
        cordaCompile "$corda_release_distribution:corda-node-api:$corda_release_version"
        cordaCompile "$corda_release_distribution:corda-webserver-impl:$corda_release_version"
        cordaRuntime "$corda_release_distribution:corda:$corda_release_version"
        cordaRuntime "$corda_release_distribution:corda-webserver:$corda_release_version"

        testCompile "$corda_release_distribution:corda-node-driver:$corda_release_version"
    }

* For existing contract ORM schemas that extend from `CommonSchemaV1.LinearState` or `CommonSchemaV1.FungibleState`,
  you will need to explicitly map the `participants` collection to a database table. Previously this mapping was done in the
  superclass, but that makes it impossible to properly configure the table name.
  The required change is to add the ``override var participants: MutableSet<AbstractParty>? = null`` field to your class, and
  add JPA mappings. For ex., see this example:

.. sourcecode:: kotlin

    @Entity
    @Table(name = "cash_states_v2",
            indexes = arrayOf(Index(name = "ccy_code_idx2", columnList = "ccy_code")))
    class PersistentCashState(

            @ElementCollection
            @Column(name = "participants")
            @CollectionTable(name="cash_states_v2_participants", joinColumns = arrayOf(
                    JoinColumn(name = "output_index", referencedColumnName = "output_index"),
                    JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id")))
            override var participants: MutableSet<AbstractParty>? = null,

Configuration
^^^^^^^^^^^^^
Applies to both gradle deployNodes tasks and/or corda node configuration (node.conf).

* Remove any references to ``networkMap``.

.. sourcecode:: shell

    networkMap "O=Agent,L=Dallas,C=US"

* Remove any references to ``advertisedServices`` (including notaries).

.. sourcecode:: shell

    advertisedServices = ["corda.notary.validating"]

* Add an explicit notary definition in the Notary node configuration only:

.. sourcecode:: shell

    notary = [validating : true]

Testing
^^^^^^^

Contract tests
~~~~~~~~~~~~~~

* You must now create a ``MockServices`` object.

  ``MockServices`` provides a mock identity, key and storage service. ``MockServices`` takes as its first argument a
  list of the CorDapp packages to scan:

  .. sourcecode:: kotlin

    private val ledgerServices = MockServices(listOf("net.corda.examples.obligation", "net.corda.testing.contracts"))

  ``MockServices`` replaces the use of ``setCordappPackages`` and ``unsetCordappPackages``.

* ``ledger`` is now defined as a ``MockServices`` method. This means that:

  .. sourcecode:: kotlin

     ledger {

  Becomes:

  .. sourcecode:: kotlin

     ledgerServices.ledger {

* Within a mock ledger transaction, ``ContractState`` instances are passed to ``input`` and ``output`` as objects
  rather than lambdas. For example:

  .. sourcecode:: kotlin

     ledgerServices.ledger {
         transaction {
             input(OBLIGATION_CONTRACT_ID, DummyState())
             output(OBLIGATION_CONTRACT_ID, oneDollarObligation)
         }
     }

* Within a mock ledger transaction, ``CommandData`` instances are passed to ``input`` and ``output`` as objects
  rather than lambdas, and the public keys must be passed as a list if there is more than one. For example:

  .. sourcecode:: kotlin

     ledgerServices.ledger {
         transaction {
             command(alice.publicKey, ObligationContract.Commands.Issue())
             command(listOf(alice.publicKey, bob.publicKey), ObligationContract.Commands.Issue())
         }
     }

* The predefined test identities (e.g. ``ALICE`` and ``MINI_CORP``) have been removed.

  You must now define the test identities explicitly. For example:

  .. sourcecode:: kotlin

     val alice = TestIdentity(CordaX500Name(organisation = "Alice", locality = "TestLand", country = "GB"))

  ``TestIdentity`` exposes methods to get the ``name``, ``keyPair``, ``publicKey``, ``party`` and ``identity`` of the
  underlying ``TestIdentity``

* Explicit invocation of transaction transformation (ie. using ``TransactionBuilder``) requires serialization engine
  to be initialized. In unit test this can be achieved by using the following jUnit rule:

  .. sourcecode:: kotlin

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

Flow tests
~~~~~~~~~~

* The registration mechanism for CorDapps in ``MockNetwork`` unit tests has changed.

  It is now done via the ``cordappPackages`` constructor parameter of MockNetwork. This takes a list of ``String``
  values which should be the package names of the CorDapps containing the contract verification code you wish to load.
  The ``unsetCordappPackages`` method is now redundant and has been removed.

* Creation of Notaries in ``MockNetwork`` unit tests has changed.

  Previously the API call ``createNotaryNode(legalName = CordaX500ame(...))`` would be used to create a notary:

  .. sourcecode:: kotlin

     val notary = mockNetwork.createNotaryNode(legalName = CordaX500Name("Notary", "London", "UK"))

  Notaries are now defined as part of ``MockNetwork`` creation using ``notarySpecs``, as in the following example:

  .. sourcecode:: kotlin

     mockNetwork = MockNetwork(notarySpecs = listOf(MockNetwork.NotarySpec(CordaX500Name("Notary","London","UK"))))

* A notary is no longer specified when creating a standard node using the ``createPartyNode`` API call.

  Previously:

  .. sourcecode:: kotlin

     mockNetwork.createPartyNode(notary.network.myAddress, CordaX500Name("Node", "Madrid", "ES"))

  Becomes:

  .. sourcecode:: kotlin

     mockNetwork.createPartyNode(CordaX500Name("Node", "Madrid", "ES"))

* Utility node creation API method ``createSomeNodes(...)`` has been removed, and nodes must be created individually.

  Previously:

  .. sourcecode:: java

     MockNetwork.BasketOfNodes nodes = net.createSomeNodes(3);
     nodeA = nodes.getPartyNodes().get(0);
     nodeB = nodes.getPartyNodes().get(1);
     nodeC = nodes.getPartyNodes().get(2);

  Becomes:

  .. sourcecode:: java

     nodeA = net.createNode(new MockNodeParameters());
     nodeB = net.createNode(new MockNodeParameters());
     nodeC = net.createNode(new MockNodeParameters());
     List<StartedNode<MockNode>> nodes = Arrays.asList(nodeA, nodeB, nodeC);

* Flow framework instantiation of a flow has a slight variation in start syntax:

  Previously:

  .. sourcecode:: java

     CordaFuture<SignedTransaction> future = nodeA.getServices().startFlow(flow).getResultFuture();

  Becomes:

  .. sourcecode:: java

     CordaFuture<SignedTransaction> future = startFlow(nodeA.getServices(), flow).getResultFuture();

* ``StartedNodeServices.startFlow`` must now be imported from ``net.corda.testing.node``

* Do not use ``node.internals`` to register flows:

  Previous code would often look as follows:

  .. sourcecode:: kotlin

     protected fun registerFlowsAndServices(node: StartedNode<MockNetwork.MockNode>) {
         val mockNode = node.internals
         mockNode.registerInitiatedFlow(MyCustomFlow::class.java)
     }

  Becomes:

  .. sourcecode:: kotlin

     protected fun registerFlowsAndServices(mockNode: StartedNode<MockNetwork.MockNode>) {
         mockNode.registerInitiatedFlow(MyCustomFlow::class.java)
     }

* Do not use ``node.internals`` to register Corda services

  Previously:

  .. sourcecode:: kotlin

  node.internals.installCordaService(CustomService::class.java)

  Becomes:

  .. sourcecode:: kotlin

  node.services.cordaService(CustomService::class.java)

Better yet, use node factory to organize both register flows and services, for example, create class as follows:

  .. sourcecode:: kotlin

  class PrimesOracleNode(args: MockNodeArgs) : MockNetwork.MockNode(args) {
        override fun start() = super.start().apply {
            registerInitiatedFlow(QueryHandler::class.java)
            registerInitiatedFlow(SignHandler::class.java)
                    services.cordaService(net.corda.examples.oracle.service.service.Oracle::class.java)
        }
    }

  and then pass it to ``createNode``:

  .. sourcecode:: kotlin

  val oracle = mockNet.createNode(MockNodeParameters(legalName = CordaX500Name("Oracle", "New York", "US")), ::PrimesOracleNode)

Node driver
~~~~~~~~~~~

* ``User`` has been moved from ``net.corda.nodeapi.User`` to ``net.corda.nodeapi.internal.config.User``

* Notaries are defined by passing a list of ``NotarySpec`` objects to ``driver`` using the ``notarySpecs`` argument,
  instead of being defined manually in the driver block.

  ``notarySpecs`` defaults to providing a single validating notary

* The ``waitForAllNodesToFinish`` function has been removed. It has been replaced with a ``waitForAllNodesToFinish``
  argument to ``driver``

* No longer specify advertised services to the ``DriverDSL`` when starting nodes:

  Previously:

  .. sourcecode:: kotlin

     driver {
         startNode(providedName = CordaX500Name("Controller", "London", "GB"), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))

  Becomes:

  .. sourcecode:: kotlin

     driver {
         startNode(providedName = CordaX500Name("Controller", "London", "GB")),

Finance
^^^^^^^

* ``CASH_PROGRAM_ID`` has been moved to ``Cash.PROGRAM_ID``, where ``Cash`` is defined in the
  ``import net.corda.finance.contracts.asset`` package

V1.0 to V2.0
------------

You only need to update the ``corda_release_version`` identifier in your project gradle file. The
corda_gradle_plugins_version should remain at 1.0.0:

.. sourcecode:: shell

    ext.corda_release_version = '2.0.0'
    ext.corda_gradle_plugins_version = '1.0.0'

Public Beta (M12) to V1.0
-------------------------

:ref:`From Milestone 14 <changelog_m14>`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Build
^^^^^

* MockNetwork has moved.

  A new test driver module dependency needs to be including in your project: ``corda-node-driver``. To continue using the
  mock network for testing, add the following entry to your gradle build file:

.. sourcecode:: shell

  testCompile "$corda_release_distribution:corda-node-driver:$corda_release_version"

.. note::  you may only need `testCompile "$corda_release_distribution:corda-test-utils:$corda_release_version"` if not using the Driver DSL.

Configuration
^^^^^^^^^^^^^

* ``CordaPluginRegistry`` has been removed.
  The one remaining configuration item ``customizeSerialisation``, which defined a optional whitelist of types for use in
  object serialization, has been replaced with the ``SerializationWhitelist`` interface which should be implemented to
  define a list of equivalent whitelisted classes.
  You will need to rename your services resource file to the new class name:
  'resources/META-INF/services/net.corda.core.node.CordaPluginRegistry' becomes 'resources/META-INF/services/net.corda.core.serialization.SerializationWhitelist'
  An associated property on ``MockNode`` was renamed from ``testPluginRegistries`` to ``testSerializationWhitelists``.
  In general, the ``@CordaSerializable`` annotation is the preferred method for whitelisting as described in :doc:`serialization`

Missing imports
^^^^^^^^^^^^^^^

Use the automatic imports feature of IntelliJ to intelligently resolve the new imports.

* Missing imports for contract types.

  CommercialPaper and Cash are now contained within the ``finance`` module, as are associated helpers functions.
  For example:
    ``import net.corda.contracts.ICommercialPaperState`` becomes ``import net.corda.finance.contracts.ICommercialPaperState``

    ``import net.corda.contracts.asset.sumCashBy`` becomes ``import net.corda.finance.utils.sumCashBy``

    ``import net.corda.core.contracts.DOLLARS`` becomes ``import net.corda.finance.DOLLARS``

    ``import net.corda.core.contracts.issued by`` becomes ``import net.corda.finance.issued by``

    ``import net.corda.contracts.asset.Cash`` becomes ``import net.corda.finance.contracts.asset.Cash``

* Missing imports for utility functions.

  Many common types and helper methods have been consolidated into ``net.corda.core.utilities`` package.
  For example:
    ``import net.corda.core.crypto.commonName`` becomes ``import net.corda.core.utilities.commonName``

    ``import net.corda.core.crypto.toBase58String`` becomes ``import net.corda.core.utilities.toBase58String``

    ``import net.corda.core.getOrThrow`` becomes ``import net.corda.core.utilities.getOrThrow``

* Missing flow imports.

  In general all reusable library flows are contained within the **core** API `net.corda.core.flows` package.
  Financial domain library flows are contained within the **finance** module `net.corda.finance.flows` package.
  Other flows that have moved include:

  ``import net.corda.core.flows.ResolveTransactionsFlow`` becomes ``import net.corda.core.internal.ResolveTransactionsFlow``

Core data structures
^^^^^^^^^^^^^^^^^^^^

* Missing Contract override.

  The contract interace attribute ``legalContractReference`` has been removed, and replaced by
  the optional annotation ``@LegalProseReference(uri = "<URI>")``

* Unresolved reference.

  Calls to ``AuthenticatedObject`` are replaced by ``CommandWithParties``

* Overrides nothing: ``isRelevant`` in ``LinearState``.

  Removed the concept of relevancy from ``LinearState``. A ``ContractState``'s relevance to the vault is now resolved
  internally; the vault will process any transaction from a flow which is not derived from transaction resolution verification.
  The notion of relevancy is subject to further improvements to enable a developer to control what state the vault thinks
  are relevant.

* Calls to ``txBuilder.toLedgerTransaction()`` now requires a serviceHub parameter.

  Used by the new Contract Constraints functionality to validate and resolve attachments.   

Flow framework
^^^^^^^^^^^^^^

* Flow session deprecations

  ``FlowLogic`` communication has been upgraded to use functions on ``FlowSession`` as the base for communication
  between nodes.

  * Calls to ``send()``, ``receive()`` and ``sendAndReceive()`` on FlowLogic should be replaced with calls
    to the function of the same name on ``FlowSession``. Note that the replacement functions do not take in a destination
    parameter, as this is defined in the session.

  * Initiated flows now take in a ``FlowSession`` instead of ``Party`` in their constructor. If you need to access the
    counterparty identity, it is in the ``counterparty`` property of the flow session.

  See ``FlowSession`` for step by step instructions on porting existing flows to use the new mechanism.

* ``FinalityFlow`` now returns a single ``SignedTransaction``, instead of a ``List<SignedTransaction>``

* ``TransactionKeyFlow`` renamed to ``SwapIdentitiesFlow``

  Note that ``SwapIdentitiesFlow`` must be imported from the *confidential-identities** package ''net.corda.confidential''

Node services (ServiceHub)
^^^^^^^^^^^^^^^^^^^^^^^^^^

* VaultQueryService: unresolved reference to ``vaultQueryService``.

  Replace all references to ``<services>.vaultQueryService`` with ``<services>.vaultService``.
  Previously there were two vault APIs. Now there is a single unified API with the same functions: ``VaultService``.

* ``serviceHub.myInfo.legalIdentity`` no longer exists; use the ``ourIdentity`` property of the flow instead.

  ``FlowLogic.ourIdentity`` has been introduced as a shortcut for retrieving our identity in a flow

* ``getAnyNotary`` is gone - use ``serviceHub.networkMapCache.notaryIdentities[0]`` instead

   Note: ongoing work to support multiple notary identities is still in progress.

* ``ServiceHub.networkMapUpdates`` is replaced by ``ServiceHub.networkMapFeed``

* ``ServiceHub.partyFromX500Name`` is replaced by ``ServiceHub.wellKnownPartyFromX500Name``
  Note: A "well known" party is one that isn't anonymous and this change was motivated by the confidential identities work.

RPC Client
^^^^^^^^^^

* Missing API methods on ``CordaRPCOps`` interface.

  * Calls to ``verifiedTransactionsFeed()`` and ``verifiedTransactions()`` have been replaced with:
    ``internalVerifiedTransactionsSnapshot()`` and ``internalVerifiedTransactionsFeed()`` respectively

    This is in preparation for the planned integration of Intel SGX™, which will encrypt the transactions feed.
    Apps that use this API will not work on encrypted ledgers: you should probably be using the vault query API instead.

  * Accessing the ``networkMapCache`` via ``services.nodeInfo().legalIdentities`` returns a list of identities.
    The first element in the list is the Party object referring to a node's single identity.

    This is in preparation for allowing a node to host multiple separate identities in future.

Testing
^^^^^^^

Please note that ``Clauses`` have been removed completely as of V1.0.
We will be revisiting this capability in a future release.

* CorDapps must be explicitly registered in ``MockNetwork`` unit tests.

  This is done by calling ``setCordappPackages``, an extension helper function in the ``net.corda.testing`` package,
  on the first line of your ``@Before`` method. This takes a variable number of ``String`` arguments which should be the
  package names of the CorDapps containing the contract verification code you wish to load.
  You should unset CorDapp packages in your ``@After`` method by using ``unsetCordappPackages()`` after ``stopNodes()``.

* CorDapps must be explicitly registered in ``DriverDSL`` and ``RPCDriverDSL`` integration tests.

  Similarly, you must also register package names of the CorDapps containing the contract verification code you wish to load
  using the ``extraCordappPackagesToScan: List<String>`` constructor parameter of the driver DSL.

Finance
^^^^^^^

* ``FungibleAsset`` interface simplification.

  The ``FungibleAsset`` interface has been made simpler. The ``Commands`` grouping interface
  that included the ``Move``, ``Issue`` and ``Exit`` interfaces have all been removed, while the ``move`` function has
  been renamed to ``withNewOwnerAndAmount`` to be consistent with the ``withNewOwner`` function of the ``OwnableState``.

  The following errors may be reported:

  * override nothing (FungibleAsset): ``move``
  * not a subtype of overridden FungibleAsset: ``withNewOwner``
  * no longer need to override ``override val contractHash: SecureHash? = null``
  * need to override ``override val contract: Class<out Contract>? = null``


Miscellaneous
^^^^^^^^^^^^^

* ``args[0].parseNetworkHostAndPort()`` becomes ``NetworkHostAndPort.parse(args[0])``

* There is no longer a ``NodeInfo.advertisedServices`` property.

  The concept of advertised services has been removed from Corda. This is because it was vaguely defined and real world
  apps would not typically select random, unknown counterparties from the network map based on self-declared capabilities.
  We will introduce a replacement for this functionality, business networks, in a future release.

  For now, your should retrieve the service by legal name using ``NetworkMapCache.getNodeByLegalName``.

Gotchas
^^^^^^^

* Beware to use the correct identity when issuing cash:

  The 3rd parameter to ``CashIssueFlow`` should be the ** notary ** (not the ** node identity **)


:ref:`From Milestone 13 <changelog_m13>`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Core data structures
^^^^^^^^^^^^^^^^^^^^

* ``TransactionBuilder`` changes.

  Use convenience class ``StateAndContract`` instead of ``TransactionBuilder.withItems()`` for passing
  around a state and its contract.

* Transaction building DSL changes:

  * now need to explicitly pass the ContractClassName into all inputs and outputs.
  * ``ContractClassName`` refers to the class containing the “verifier” method.

* Contract verify method signature change.

  ``override fun verify(tx: TransactionForContract)`` becomes ``override fun verify(tx: LedgerTransaction)``

* No longer need to override Contract ``contract()`` function.

Node services (ServiceHub)
^^^^^^^^^^^^^^^^^^^^^^^^^^

* ServiceHub API method changes.

  ``services.networkMapUpdates().justSnapshot`` becomes ``services.networkMapSnapshot()``

Configuration
^^^^^^^^^^^^^

* No longer need to define ``CordaPluginRegistry`` and configure ``requiredSchemas``

  Custom contract schemas are automatically detected at startup time by class path scanning.
  For testing purposes, use the ``SchemaService`` method to register new custom schemas:
  eg. ``services.schemaService.registerCustomSchemas(setOf(YoSchemaV1))``

Identity
^^^^^^^^

* Party names are now ``CordaX500Name``, not ``X500Name``

  ``CordaX500Name`` specifies a predefined set of mandatory (organisation, locality, country)
  and optional fields (commonName, organisationUnit, state) with validation checking.
  Use new builder CordaX500Name.build(X500Name(target)) or, preferably, explicitly define X500Name parameters using
  ``CordaX500Name`` constructor.

Testing
^^^^^^^

* MockNetwork Testing.

  Mock nodes in node tests are now of type ``StartedNode<MockNode>``, rather than ``MockNode``
  MockNetwork now returns a BasketOf(<StartedNode<MockNode>>)
  Must call internals on StartedNode to get MockNode:
    a = nodes.partyNodes[0].internals
    b = nodes.partyNodes[1].internals

* Host and Port change.

  Use string helper function ``parseNetworkHostAndPort()`` to parse a URL on startup.
   eg. ``val hostAndPort = args[0].parseNetworkHostAndPort()``

* The node driver parameters for starting a node have been reordered, and the node’s name needs to be given as an
  ``CordaX500Name``, instead of using ``getX509Name``


:ref:`From Milestone 12 (First Public Beta) <changelog_m12>`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Core data structures
^^^^^^^^^^^^^^^^^^^^

* Transaction building

  You no longer need to specify the type of a ``TransactionBuilder`` as ``TransactionType.General``
  ``TransactionType.General.Builder(notary)`` becomes ``TransactionBuilder(notary)``

Build 
^^^^^

* Gradle dependency reference changes.

  Module name has changed to include ``corda`` in the artifacts jar name:

.. sourcecode:: shell

    compile "$corda_release_distribution:core:$corda_release_version" -> compile "$corda_release_distribution:corda-core:$corda_release_version"
    compile "$corda_release_distribution:finance:$corda_release_version" -> compile "$corda_release_distribution:corda-finance:$corda_release_version"
    compile "$corda_release_distribution:jackson:$corda_release_version" -> compile "$corda_release_distribution:corda-jackson:$corda_release_version"
    compile "$corda_release_distribution:node:$corda_release_version" -> compile "$corda_release_distribution:corda-node:$corda_release_version"
    compile "$corda_release_distribution:rpc:$corda_release_version" -> compile "$corda_release_distribution:corda-rpc:$corda_release_version"

Node services (ServiceHub)
^^^^^^^^^^^^^^^^^^^^^^^^^^

* ServiceHub API changes.

  ``services.networkMapUpdates()`` becomes ``services.networkMapFeed()``
  ``services.getCashBalances()`` becomes a helper method within the **finance** module contracts package: ``net.corda.finance.contracts.getCashBalances``

Finance
^^^^^^^

* Financial asset contracts (Cash, CommercialPaper, Obligations) are now a standalone CorDapp within the **finance** module.

  Need to import from respective package within ``finance`` module:
    eg. ``net.corda.finance.contracts.asset.Cash``

  Likewise, need to import associated asset flows from respective package within ``finance`` module:
    eg. ``net.corda.finance.flows.CashIssueFlow``
        ``net.corda.finance.flows.CashIssueAndPaymentFlow``
        ``net.corda.finance.flows.CashExitFlow``

* Moved ``finance`` gradle project files into a ``net.corda.finance`` package namespace.

  This may require adjusting imports of Cash flow references and also of ``StartFlow`` permission in ``gradle.build`` files.
  Associated flows (``Cash*Flow``, ``TwoPartyTradeFlow``, ``TwoPartyDealFlow``) must now be imported from this package.
