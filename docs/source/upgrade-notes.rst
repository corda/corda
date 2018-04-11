Upgrading a CorDapp to a new platform version
=============================================

These notes provide instructions for upgrading your CorDapps from previous versions, starting with the upgrade from our
first public Beta (:ref:`Milestone 12 <changelog_m12>`), to :ref:`V1.0 <changelog_v1>`.

.. contents::
   :depth: 3

General rules
-------------
Always remember to update the version identifiers in your project gradle file:

.. sourcecode:: shell

    ext.corda_release_version = '1.0.0'
    ext.corda_gradle_plugins_version = '1.0.0'

It may be necessary to update the version of major dependencies:

.. sourcecode:: shell

    ext.kotlin_version = '1.1.4'
    ext.quasar_version = '0.7.9'

Please consult the relevant release notes of the release in question. If not specified, you may assume the
versions you are currently using are still in force.

We also strongly recommend cross referencing with the :doc:`changelog` to confirm changes.

UNRELEASED
----------

<<< Fill this in >>>

v3.0 to v3.1
------------

Gradle Plugin Version
^^^^^^^^^^^^^^^^^^^^^

Corda 3.1 uses version 3.1.0 of the gradle plugins and your ``build.gradle`` file should be updated to reflect this.

.. sourcecode:: shell

    ext.corda_gradle_plugins_version = '3.1.0'

You will also need to update the ``corda_release_version`` identifier in your project gradle file.

.. sourcecode:: shell

  ext.corda_release_version = '3.1-corda'

V2.0 to V3.0
------------

Gradle Plugin Version
^^^^^^^^^^^^^^^^^^^^^

Corda 3.0 uses version 3.0.9 of the gradle plugins and your ``build.gradle`` file should be updated to reflect this.

.. sourcecode:: shell

    ext.corda_gradle_plugins_version = '3.0.9'

You will also need to update the ``corda_release_version`` identifier in your project gradle file.

.. sourcecode:: shell

  ext.corda_release_version = 'corda-3.0'

Network Map Service
^^^^^^^^^^^^^^^^^^^

With the re-designed network map service the following changes need to be made:

* The network map is no longer provided by a node and thus the ``networkMapService`` config is ignored. Instead the
  network map is either provided by the compatibility zone (CZ) operator (who operates the doorman) and available
  using the ``compatibilityZoneURL`` config, or is provided using signed node info files which are copied locally.
  See :doc:`network-map` for more details, and :doc:`setting-up-a-corda-network.rst` on how to use the network
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
  For example:

    .. sourcecode:: groovy
        dependencies {
            classpath "net.corda.plugins:cordapp:$corda_gradle_plugins_version"
        }

        apply plugin: 'net.corda.plugins.cordapp'

The plugin needs to be applied in all gradle build files where there is a dependency on Corda using any of:
cordaCompile, cordaRuntime, cordapp

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

Testing
~~~~~~~

* The registration mechanism for CorDapps in ``MockNetwork`` unit tests has changed:

  * CorDapp registration is now done via the ``cordappPackages`` constructor parameter of MockNetwork. This parameter
    is a list of ``String`` values which should be the package names of the CorDapps containing the contract
    verification code you wish to load

  * The ``unsetCordappPackages`` method is now redundant and has been removed

V1.0 to V2.0
------------

* You need to update the ``corda_release_version`` identifier in your project gradle file. The
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

* MockNetwork has moved. To continue using ``MockNetwork`` for testing, you must add the following dependency to your
  ``build.gradle`` file:

    .. sourcecode:: shell

      testCompile "net.corda:corda-node-driver:$corda_release_version"

    .. note:: You may only need ``testCompile "net.corda:corda-test-utils:$corda_release_version"`` if not using the Driver
       DSL

Configuration
^^^^^^^^^^^^^

* ``CordaPluginRegistry`` has been removed:

  * The one remaining configuration item ``customizeSerialisation``, which defined a optional whitelist of types for
    use in object serialization, has been replaced with the ``SerializationWhitelist`` interface which should be
    implemented to define a list of equivalent whitelisted classes

  * You will need to rename your services resource file. 'resources/META-INF/services/net.corda.core.node.CordaPluginRegistry'
    becomes 'resources/META-INF/services/net.corda.core.serialization.SerializationWhitelist'

  * ``MockNode.testPluginRegistries`` was renamed to ``MockNode.testSerializationWhitelists``

  * In general, the ``@CordaSerializable`` annotation is the preferred method for whitelisting, as described in
    :doc:`serialization`

Missing imports
^^^^^^^^^^^^^^^

Use IntelliJ's automatic imports feature to intelligently resolve the new imports:

* Missing imports for contract types:

  * CommercialPaper and Cash are now contained within the ``finance`` module, as are associated helpers functions. For
    example:

    * ``import net.corda.contracts.ICommercialPaperState`` becomes ``import net.corda.finance.contracts.ICommercialPaperState``

    * ``import net.corda.contracts.asset.sumCashBy`` becomes ``import net.corda.finance.utils.sumCashBy``

    * ``import net.corda.core.contracts.DOLLARS`` becomes ``import net.corda.finance.DOLLARS``

    * ``import net.corda.core.contracts.issued by`` becomes ``import net.corda.finance.issued by``

    * ``import net.corda.contracts.asset.Cash`` becomes ``import net.corda.finance.contracts.asset.Cash``

* Missing imports for utility functions:

  * Many common types and helper methods have been consolidated into ``net.corda.core.utilities`` package. For example:

    * ``import net.corda.core.crypto.commonName`` becomes ``import net.corda.core.utilities.commonName``

    * ``import net.corda.core.crypto.toBase58String`` becomes ``import net.corda.core.utilities.toBase58String``

    * ``import net.corda.core.getOrThrow`` becomes ``import net.corda.core.utilities.getOrThrow``

* Missing flow imports:

  * In general, all reusable library flows are contained within the **core** API ``net.corda.core.flows`` package

  * Financial domain library flows are contained within the **finance** module ``net.corda.finance.flows`` package

  * Other flows that have moved include ``import net.corda.core.flows.ResolveTransactionsFlow``, which becomes
    ``import net.corda.core.internal.ResolveTransactionsFlow``

Core data structures
^^^^^^^^^^^^^^^^^^^^

* Missing ``Contract`` override:

  * ``Contract.legalContractReference`` has been removed, and replaced by the optional annotation
    ``@LegalProseReference(uri = "<URI>")``

* Unresolved reference:

  * ``AuthenticatedObject`` was renamed to ``CommandWithParties``

* Overrides nothing:

  * ``LinearState.isRelevant`` was removed. Whether a node stores a ``LinearState`` in its vault depends on whether the
    node is one of the state's ``participants``

  * ``txBuilder.toLedgerTransaction`` now requires a ``ServiceHub`` parameter. This is used by the new Contract
    Constraints functionality to validate and resolve attachments

Flow framework
^^^^^^^^^^^^^^

* ``FlowLogic`` communication has been upgraded to use explicit ``FlowSession`` instances to communicate between nodes:

  * ``FlowLogic.send``/``FlowLogic.receive``/``FlowLogic.sendAndReceive`` has been replaced by ``FlowSession.send``/
    ``FlowSession.receive``/``FlowSession.sendAndReceive``. The replacement functions do not take a destination
    parameter, as this is defined implictly by the session used

  * Initiated flows now take in a ``FlowSession`` instead of ``Party`` in their constructor. If you need to access the
    counterparty identity, it is in the ``counterparty`` property of the flow session

* ``FinalityFlow`` now returns a single ``SignedTransaction``, instead of a ``List<SignedTransaction>``

* ``TransactionKeyFlow`` was renamed to ``SwapIdentitiesFlow``

* ``SwapIdentitiesFlow`` must be imported from the *confidential-identities* package ``net.corda.confidential``

Node services (ServiceHub)
^^^^^^^^^^^^^^^^^^^^^^^^^^

* Unresolved reference to ``vaultQueryService``:

  * Replace all references to ``<services>.vaultQueryService`` with ``<services>.vaultService``

  * Previously there were two vault APIs. Now there is a single unified API with the same functions: ``VaultService``.

* ``FlowLogic.ourIdentity`` has been introduced as a shortcut for retrieving our identity in a flow

* ``serviceHub.myInfo.legalIdentity`` no longer exists

* ``getAnyNotary`` has been removed. Use ``serviceHub.networkMapCache.notaryIdentities[0]`` instead

* ``ServiceHub.networkMapUpdates`` is replaced by ``ServiceHub.networkMapFeed``

* ``ServiceHub.partyFromX500Name`` is replaced by ``ServiceHub.wellKnownPartyFromX500Name``

  * A "well known" party is one that isn't anonymous. This change was motivated by the confidential identities work

RPC Client
^^^^^^^^^^

* Missing API methods on the ``CordaRPCOps`` interface:

  * ``verifiedTransactionsFeed`` has been replaced by ``internalVerifiedTransactionsFeed``

  * ``verifiedTransactions`` has been replaced by ``internalVerifiedTransactionsSnapshot``

  * These changes are in preparation for the planned integration of Intel SGX™, which will encrypt the transactions
    feed. Apps that use this API will not work on encrypted ledgers. They should generally be modified to use the vault
    query API instead

  * Accessing the ``networkMapCache`` via ``services.nodeInfo().legalIdentities`` returns a list of identities

    * This change is in preparation for allowing a node to host multiple separate identities in the future

Testing
^^^^^^^

Please note that ``Clauses`` have been removed completely as of V1.0. We will be revisiting this capability in a future
release.

* CorDapps must be explicitly registered in ``MockNetwork`` unit tests:

  * This is done by calling ``setCordappPackages``, an extension helper function in the ``net.corda.testing`` package,
    on the first line of your ``@Before`` method. This takes a variable number of ``String`` arguments which should be
    the package names of the CorDapps containing the contract verification code you wish to load
  * You should unset CorDapp packages in your ``@After`` method by using ``unsetCordappPackages`` after
    ``stopNodes``

* CorDapps must be explicitly registered in ``DriverDSL`` and ``RPCDriverDSL`` integration tests:

  * You must register package names of the CorDapps containing the contract verification code you wish to load using
    the ``extraCordappPackagesToScan: List<String>`` constructor parameter of the driver DSL

Finance
^^^^^^^

* ``FungibleAsset`` interface simplification:

  * The ``Commands`` grouping interface that included the ``Move``, ``Issue`` and ``Exit`` interfaces has been removed
  * The ``move`` function has been renamed to ``withNewOwnerAndAmount``
    * This is for consistency with ``OwnableState.withNewOwner``

Miscellaneous
^^^^^^^^^^^^^

* ``args[0].parseNetworkHostAndPort()`` becomes ``NetworkHostAndPort.parse(args[0])``

* There is no longer a ``NodeInfo.advertisedServices`` property

  * The concept of advertised services has been removed from Corda. This is because it was vaguely defined and
    real-world apps would not typically select random, unknown counterparties from the network map based on
    self-declared capabilities
  * We will introduce a replacement for this functionality, business networks, in a future release
  * For now, services should be retrieved by legal name using ``NetworkMapCache.getNodeByLegalName``

Gotchas
^^^^^^^

* Be sure to use the correct identity when issuing cash:

  * The third parameter to ``CashIssueFlow`` should be the *notary* (and not the *node identity*)


:ref:`From Milestone 13 <changelog_m13>`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Core data structures
^^^^^^^^^^^^^^^^^^^^

* ``TransactionBuilder`` changes:

  * Use convenience class ``StateAndContract`` instead of ``TransactionBuilder.withItems`` for passing
    around a state and its contract.

* Transaction builder DSL changes:

  * When adding inputs and outputs to a transaction builder, you must also specify ``ContractClassName``

    * ``ContractClassName`` is the name of the ``Contract`` subclass used to verify the transaction

* Contract verify method signature change:

  * ``override fun verify(tx: TransactionForContract)`` becomes ``override fun verify(tx: LedgerTransaction)``

* You no longer need to override ``ContractState.contract`` function

Node services (ServiceHub)
^^^^^^^^^^^^^^^^^^^^^^^^^^

* ServiceHub API method changes:

  * ``services.networkMapUpdates().justSnapshot`` becomes ``services.networkMapSnapshot()``

Configuration
^^^^^^^^^^^^^

* No longer need to define ``CordaPluginRegistry`` and configure ``requiredSchemas``:

  * Custom contract schemas are automatically detected at startup time by class path scanning

  * For testing purposes, use the ``SchemaService`` method to register new custom schemas (e.g.
    ``services.schemaService.registerCustomSchemas(setOf(YoSchemaV1))``)

Identity
^^^^^^^^

* Party names are now ``CordaX500Name``, not ``X500Name``:

  * ``CordaX500Name`` specifies a predefined set of mandatory (organisation, locality, country) and optional fields
    (common name, organisation unit, state) with validation checking
  * Use new builder ``CordaX500Name.build(X500Name(target))`` or explicitly define the X500Name parameters using the
    ``CordaX500Name`` constructors

Testing
^^^^^^^

* MockNetwork testing:

  * Mock nodes in node tests are now of type ``StartedNode<MockNode>``, rather than ``MockNode``

  * ``MockNetwork`` now returns a ``BasketOf(<StartedNode<MockNode>>)``

  * You must call internals on ``StartedNode`` to get ``MockNode`` (e.g. ``a = nodes.partyNodes[0].internals``)

* Host and port changes:

  * Use string helper function ``parseNetworkHostAndPort`` to parse a URL on startup (e.g.
    ``val hostAndPort = args[0].parseNetworkHostAndPort()``)

* Node driver parameter changes:

  * The node driver parameters for starting a node have been reordered
  * The node’s name needs to be given as an ``CordaX500Name``, instead of using ``getX509Name``

:ref:`From Milestone 12 (First Public Beta) <changelog_m12>`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Core data structures
^^^^^^^^^^^^^^^^^^^^

* Transaction building:

  * You no longer need to specify the type of a ``TransactionBuilder`` as ``TransactionType.General``
  * ``TransactionType.General.Builder(notary)`` becomes ``TransactionBuilder(notary)``

Build 
^^^^^

* Gradle dependency reference changes:

  * Module names have changed to include ``corda`` in the artifacts' JAR names:

.. sourcecode:: shell

    compile "net.corda:core:$corda_release_version" -> compile "net.corda:corda-core:$corda_release_version"
    compile "net.corda:finance:$corda_release_version" -> compile "net.corda:corda-finance:$corda_release_version"
    compile "net.corda:jackson:$corda_release_version" -> compile "net.corda:corda-jackson:$corda_release_version"
    compile "net.corda:node:$corda_release_version" -> compile "net.corda:corda-node:$corda_release_version"
    compile "net.corda:rpc:$corda_release_version" -> compile "net.corda:corda-rpc:$corda_release_version"

Node services (ServiceHub)
^^^^^^^^^^^^^^^^^^^^^^^^^^

* ``ServiceHub`` API changes:

  * ``services.networkMapUpdates`` becomes ``services.networkMapFeed``

  * ``services.getCashBalances`` becomes a helper method in the *finance* module contracts package
    (``net.corda.finance.contracts.getCashBalances``)

Finance
^^^^^^^

* Financial asset contracts (``Cash``, ``CommercialPaper``, ``Obligations``) are now a standalone CorDapp within the
  ``finance`` module:

  * You need to import them from their respective packages within the ``finance`` module (e.g.
    ``net.corda.finance.contracts.asset.Cash``)

  * You need to import the associated asset flows from their respective packages within ``finance`` module. For
    example:

    * ``net.corda.finance.flows.CashIssueFlow``
    * ``net.corda.finance.flows.CashIssueAndPaymentFlow``
    * ``net.corda.finance.flows.CashExitFlow``

* The ``finance`` gradle project files have been moved into a ``net.corda.finance`` package namespace:

  * Adjust imports of Cash flow references
  * Adjust the ``StartFlow`` permission in ``gradle.build`` files
  * Adjust imports of the associated flows (``Cash*Flow``, ``TwoPartyTradeFlow``, ``TwoPartyDealFlow``)
