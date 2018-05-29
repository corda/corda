Upgrading a CorDapp to a new platform version
=============================================

These notes provide instructions for upgrading your CorDapps from previous versions to :ref:`V3.0 Developer Preview <changelog_r3_v3>` of R3 Corda (Enterprise Blockchain).

.. contents::
   :depth: 3

General rules
-------------
Always remember to update the version identifiers in your project gradle file. For example, Corda V3.0 uses:

.. sourcecode:: shell

    ext.corda_release_version = 'corda-3.0'
    ext.corda_release_distribution = 'net.corda'
    ext.corda_gradle_plugins_version = '3.0.9'

It may be necessary to update the version of major dependencies. This will be clearly stated in the upgrade notes for
a particular version. For example, Corda V3.0 uses:

.. sourcecode:: shell

    ext.kotlin_version = '1.1.60'
    ext.quasar_version = '0.7.9'

Please consult the relevant release notes of the release in question. If not specified, you may assume the
versions you are currently using are still in force.

We also strongly recommend cross referencing with the :doc:`changelog` to confirm changes.

UNRELEASED
----------

<<< Fill this in >>>

* API change: ``net.corda.core.schemas.PersistentStateRef`` fields (``index`` and ``txId``) incorrectly marked as nullable are now non-nullable,
  :doc:`changelog` contains the explanation.

  H2 database upgrade action:

  For Cordapps persisting custom entities with ``PersistentStateRef`` used as non Primary Key column, the backing table needs to be updated,
  In SQL replace ``your_transaction_id``/``your_output_index`` column names with your custom names, if entity didn't used JPA ``@AttributeOverrides``
  then default names are ``transaction_id`` and ``output_index``.

  .. sourcecode:: sql

       SELECT count(*) FROM [YOUR_PersistentState_TABLE_NAME] WHERE your_transaction_id IS NULL OR your_output_index IS NULL;

  In case your table already contains rows with NULL columns, and the logic doesn't distinguish between NULL and an empty string,
  all NULL column occurrences can be changed to an empty string:

  .. sourcecode:: sql

       UPDATE [YOUR_PersistentState_TABLE_NAME] SET your_transaction_id="" WHERE your_transaction_id IS NULL;
       UPDATE [YOUR_PersistentState_TABLE_NAME] SET your_output_index="" WHERE your_output_index IS NULL;

  If all rows have NON NULL ``transaction_ids`` and ``output_idx`` or you have assigned empty string values, then it's safe to update the table:

  .. sourcecode:: sql

       ALTER TABLE [YOUR_PersistentState_TABLE_NAME] ALTER COLUMN your_transaction_id SET NOT NULL;
       ALTER TABLE [YOUR_PersistentState_TABLE_NAME] ALTER COLUMN your_output_index SET NOT NULL;

  If the table already contains rows with NULL values, and the logic caters differently between NULL and an empty string,
  and the logic has to be preserved you would need to create copy of ``PersistentStateRef`` class with different name and use the new class in your entity.

  No action is needed for default node tables as ``PersistentStateRef`` is used as Primary Key only and the backing columns are automatically not nullable
  or custom Cordapp entities using ``PersistentStateRef`` as Primary Key.

Upgrading to R3 Corda V3.0 Developer Preview
--------------------------------------------
A prerequisite to upgrade to R3 Corda V3.0 is to ensure your CorDapp is upgraded to Open Source Corda V3.x.
Please follow the instructions in the "Upgrading to V3.x" section to complete this initial step.

Upgrading to R3 Corda is now a simple task of updating the version identifiers as follows:

.. sourcecode:: shell

    ext.corda_release_distribution = 'com.r3.corda'                 // R3 Corda
    ext.corda_release_version = 'R3.CORDA-3.0.0-DEV-PREVIEW-3'      // R3 Corda
    ext.corda_gradle_plugins_version = '4.0.9'

and specifying an additional repository entry to point to the location of the R3 Corda distribution:

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

Upgrading to V3.x
-----------------

Please refer to:

* `Corda V3.1 upgrade notes <https://docs.corda.net/releases/release-V3.1/upgrade-notes.html#v3-0-to-v3-1>`_

* `Corda V3.0 upgrade notes <https://docs.corda.net/releases/release-V3.0/upgrade-notes.html#v2-0-to-v3-0>`_

Build
^^^^^

* Update the version identifiers in your project gradle file(s):

.. sourcecode:: shell

    ext.corda_release_version = 'corda-3.0'         // Corda (Open Source)
    ext.corda_gradle_plugins_version = '4.0.9'
    ext.kotlin_version = '1.2.20'

* Add a new release identifier to specify the corda distribution type (Open Source or R3 Corda):

.. sourcecode:: shell

  ext.corda_release_distribution = 'net.corda'    // Corda (Open Source)

Network Map Service
^^^^^^^^^^^^^^^^^^^

With the re-designed network map service the following changes need to be made:

* The network map is no longer provided by a node and thus the ``networkMapService`` config is ignored. Instead the
  network map is either provided by the compatibility zone (CZ) operator (who operates the doorman) and available
  using the ``compatibilityZoneURL`` config, or is provided using signed node info files which are copied locally.
  See :doc:`network-map` for more details, and :doc:`setting-up-a-corda-network` on how to use the network
  bootstrapper for deploying a local network.

* Configuration for a notary has been simplified. ``extraAdvertisedServiceIds``, ``notaryNodeAddress``, ``notaryClusterAddresses``
  and ``bftSMaRt`` configs have been replaced by a single ``notary`` config object. See :doc:`corda-configuration-file`
  for more details.

* The advertisement of the notary to the rest of the network, and its validation type, is no longer determined by the
  ``extraAdvertisedServiceIds`` config. Instead it has been moved to the control of the network operator via
  the introduction of network parameters. The network bootstrapper automatically includes the configured notaries
  when generating the network parameters file for a local deployment.

* Any nodes defined in a ``deployNodes`` gradle task performing the function of the network map can be removed, or the
  ``NetworkMap`` parameter can be removed for any "controller" node which is both the network map and a notary.

* For registering a node with the doorman the ``certificateSigningService`` config has been replaced by ``compatibilityZoneURL``.

Corda Plugins
^^^^^^^^^^^^^

* Corda plugins have been modularised further so the following additional gradle entries are necessary:

.. sourcecode:: shell

    dependencies {
        classpath "net.corda.plugins:cordapp:$corda_gradle_plugins_version"
    }

    apply plugin: 'net.corda.plugins.cordapp'

  The plugin needs to be applied in all gradle build files where there is a dependency on Corda using any of:
  cordaCompile, cordaRuntime, cordapp


* If you use the Corda quasar-utils plugin (required for testing Corda flows), it is necessary to specify the following
  identifier information in addition to the *dependencies* and *apply* directives:
  (note, this relates to Developer Preview 3 only and will be resolved in the GA release)

.. sourcecode:: shell

    ext.quasar_group = 'com.github.corda.quasar'
    ext.quasar_version = '7629695563deae6cc95adcfbebcbc8322fd0241a'

    in addition to:

    dependencies {
        classpath "net.corda.plugins:quasar-utils:$corda_gradle_plugins_version"
    }

    apply plugin: 'net.corda.plugins.quasar-utils'

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

* For existing contract ORM schemas that extend from ``CommonSchemaV1.LinearState`` or ``CommonSchemaV1.FungibleState``,
  you will need to explicitly map the ``participants`` collection to a database table. Previously this mapping was done
  in the superclass, but that makes it impossible to properly configure the table name. The required changes are to:

  * Add the ``override var participants: MutableSet<AbstractParty>? = null`` field to your class, and
  * Add JPA mappings

  For example:

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

* Shell - to use Shell ensure ``rpcSettings.address`` and ``rpcSettings.adminAddress`` settings are present.

Databases
^^^^^^^^^

Drivers
~~~~~~~

* Alternative JDBC drivers are not bundled as part of R3 Corda releases. If you are running a node on a database different from H2 you need to provide the associated driver as described in :doc:`node-database`.

Testing
^^^^^^^

Test Framework API stabilisation changes (introduced in Corda V3.0)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* MockNetwork API usage has been greatly simplified.

  All references to ``StartedNode<MockNode>`` or ``StartedNode<MockNetwork.MockNode>`` now become ``StartedMockNode``

  Calling a flow on a MockNode now becomes ``myMockNode.startFlow(myFlow)``

* MockNode transaction demarcation has been simplified.

  All references to ``myMockNode.database.transaction { ... }`` now become ``myMockNode.transaction { ... }``

Please also see `API Testing <https://docs.corda.net/releases/release-V3.0/api-testing.html>`_

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

* The registration mechanism for CorDapps in ``MockNetwork`` unit tests has changed:

  * CorDapp registration is now done via the ``cordappPackages`` constructor parameter of MockNetwork.
    This parameter is a list of ``String`` values which should be the package names of the CorDapps containing the contract verification code you wish to load

  * The ``unsetCordappPackages`` method is now redundant and has been removed.

* Creation of Notaries in ``MockNetwork`` unit tests has changed.

  Previously the API call ``createNotaryNode(legalName = CordaX500ame(...))`` would be used to create a notary:

  .. sourcecode:: kotlin

     val notary = mockNetwork.createNotaryNode(legalName = CordaX500Name("Notary", "London", "UK"))

  Notaries are now defined as part of ``MockNetwork`` creation using a new ``MockNetworkNotarySpec`` class, as in the following example:

  .. sourcecode:: kotlin

     mockNetwork = MockNetwork(notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","UK"))))

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

* Driver instantiation now uses a new ``DriverParameters`` data class to encapsulate all available driver options.

  For example, previously:

  .. sourcecode:: kotlin

    driver(isDebug = true, waitForAllNodesToFinish = true) { ...

  Becomes:

  .. sourcecode:: kotlin

    driver(DriverParameters(isDebug = true, waitForAllNodesToFinish = true)) { ...

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
