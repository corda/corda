Changelog
=========

Here's a summary of what's changed in each Corda Enterprise release. For guidance on how to upgrade code from the previous
release, see :doc:`upgrade-notes`.

Unreleased
----------

* Changes in HA notary setup: the MySQL JDBC driver now needs to be installed manually for every worker node, otherwise nodes will fail to start.
  See :ref:`notary installation page <mysql_driver>` for more information.

Version 4.0
-----------

Please refer to :doc:`changelog` for all Open Source changes which automatically also apply to Enterprise.

Changelog entries below refer to Enterprise-only changes.

.. warning:: The ``corda-bridgserver.jar`` has been renamed to ``corda-firewall.jar`` to be more consistent
  with marketing materials and purpose of the jar. Further to this we have also renamed ``bridge.conf`` to ``firewall.conf``.
  Within that configuration file the ``bridgeMode`` property has been modified to ``firewallMode`` for overall consistency.
  Furthermore, under ``outboundConfig`` - ``socksProxyConfig`` been renamed into ``proxyConfig``.
  This will not be a breaking change for early adopters and their deployments, as new version of software can still consume
  old style configs and produce a meaningful warning.

* The experimental BFT-Smart notary implementation has been deprecated – a fully supported BFT implementation is under development.

* The experimental Raft notary implementation has been deprecated in favour of the MySQL-based HA notary implementation (see :doc:`running-a-notary-cluster/toctree`).

* Introduced a hierarchy of ``DatabaseMigrationException``'s, allowing ``NodeStartup`` to gracefully inform users of problems
  related to database migrations before exiting with a non-zero code.

* Introduced a grace period before the initial node registration fails if the node cannot connect to the Doorman.
  It retries 10 times with a 1 minute interval in between each try. At the moment this is not configurable.

* Added config log password masking for the corda firewall.

* Bridge and Float now allow keystores to have distinct private key passwords.

* Introduced a proxy timeout setting for the bridge in case the proxy is unusually slow to initate connections.
  The default value used is 10000msec.

* Added debug logging during proxy connect phase.

* Eliminated the need for a load balancer by enabling automatic RPC client failover.

* Eliminated the need for a load balancer for P2P node communications in HA configuration.

* Gracefully handle Artemis connectivity loss during bridge leader election.

* Added Server Name Indication to AMQP client/server

* Added an SNI switch.

* Enabled external bridge connectivity through HttpProxy.

* Added keytool/registration tool to combine keystores for the shared bridge deployment.

* The bridge and float keystores can have distinct passwords.

* Corda firewall should logs packet statistics.

* Implemented alternative to Zookeeper for bridge leader elections(bully algorithm).

* The default database connection pool size has been increased to correctly account for the maximum number of simultaneous database connections.
  The number of required connections has been increased by 2 + the number of RPC worker threads (typically the number of CPU cores, unless
  configured otherwise).  The total is now 3 + the number of flow worker threads + the number of RPC worker threads.

* Added support for obfuscation of node configuration content, allowing the user to mask passwords and sensitive details in configuration files.

.. _changelog_v3.1:

Version 3.1
-----------

* Update the fast-classpath-scanner dependent library version from 2.0.21 to 2.12.3

  .. note:: Whilst this is not the latest version of this library, that being 2.18.1 at time of writing, versions
     later than 2.12.3 (including 2.12.4) exhibit a different issue.

* Added `database.hibernateDialect` node configuration option

.. _changelog_r3_v3:

Corda Enterprise 3.0 Developer Preview
--------------------------------------

* Fix CORDA-1229. Setter-based serialization was broken with generic types when the property was stored as the raw type, List for example.

* Fixed security vulnerability when using the ``HashAttachmentConstraint``. Added strict check that the contract JARs
  referenced in a transaction were deployed on the node.

* Node can be shut down abruptly by ``shutdown`` function in `CordaRPCOps` or gracefully (draining flows first) through ``gracefulShutdown`` command from shell. Please refer to ::doc:`shell.rst` for more.

* Carpenter Exceptions will be caught internally by the Serializer and rethrown as a ``NotSerializableException``

  * Specific details of the error encountered are logged to the node's log file. More information can be enabled by setting the debug level to ``trace`` ; this will cause the full stack trace of the error to be dumped into the log.

* Parsing of ``NodeConfiguration`` will now fail if unknown configuration keys are found.

* The web server now has its own ``web-server.conf`` file, separate from ``node.conf``.

* Introduced a placeholder for custom properties within ``node.conf``; the property key is "custom".

* Property keys with double quotes (e.g. `"key"`) in ``node.conf`` are no longer allowed, for rationale refer to :doc:`corda-configuration-file`.

* java.math.BigInteger serialization support added.

* java.security.cert.CRLReason added to the default Whitelist.

* java.security.cert.X509CRL serialization support added.

* Added ``NetworkMapCache.getNodesByLegalName`` for querying nodes belonging to a distributed service such as a notary cluster
  where they all share a common identity. ``NetworkMapCache.getNodeByLegalName`` has been tightened to throw if more than
  one node with the legal name is found.

* Per CorDapp configuration is now exposed. ``CordappContext`` now exposes a ``CordappConfig`` object that is populated
  at CorDapp context creation time from a file source during runtime.

* Introduced Flow Draining mode, in which a node continues executing existing flows, but does not start new. This is to
  support graceful node shutdown/restarts. In particular, when this mode is on, new flows through RPC will be rejected,
  scheduled flows will be ignored, and initial session messages will not be consumed. This will ensure that the number of
  checkpoints will strictly diminish with time, allowing for a clean shutdown.

* Make the serialisation finger-printer a pluggable entity rather than hard wiring into the factory

* Removed blacklisted word checks in Corda X.500 name to allow "Server" or "Node" to be use as part of the legal name.

* Separated our pre-existing Artemis broker into an RPC broker and a P2P broker.

* Refactored ``NodeConfiguration`` to expose ``NodeRpcOptions`` (using top-level "rpcAddress" property still works with warning).

* Modified ``CordaRPCClient`` constructor to take a ``SSLConfiguration?`` additional parameter, defaulted to ``null``.

* Introduced ``CertificateChainCheckPolicy.UsernameMustMatchCommonName`` sub-type, allowing customers to optionally enforce
  username == CN condition on RPC SSL certificates.

* Modified ``DriverDSL`` and sub-types to allow specifying RPC settings for the Node.

* Modified the ``DriverDSL`` to start Cordformation nodes allowing automatic generation of "rpcSettings.adminAddress" in case
  "rcpSettings.useSsl" is ``false`` (the default).

* Introduced ``UnsafeCertificatesFactory`` allowing programmatic generation of X509 certificates for test purposes.

* JPA Mapping annotations for States extending ``CommonSchemaV1.LinearState`` and ``CommonSchemaV1.FungibleState`` on the
  `participants` collection need to be moved to the actual class. This allows to properly specify the unique table name per
  a collection. See: DummyDealStateSchemaV1.PersistentDummyDealState

* JPA Mapping annotations for States extending ``CommonSchemaV1.LinearState`` and ``CommonSchemaV1.FungibleState`` on the
  `participants` collection need to be moved to the actual State class. This allows developers to properly specify
  the table name for the `participants` collection.
  For an example on how the mapping can be done, see: DummyDealStateSchemaV1.PersistentDummyDealState

* JDBC drivers for SQL server and PostgresSQL are no longer bundled as part of Corda releases. If you are running a node
  on such databases you need to provide the associated driver as described in :doc:`node-database`.

* X.509 certificates now have an extension that specifies the Corda role the certificate is used for, and the role
  hierarchy is now enforced in the validation code. See ``net.corda.core.internal.CertRole`` for the current implementation
  until final documentation is prepared. Certificates at ``NODE_CA``, ``WELL_KNOWN_SERVICE_IDENTITY`` and above must
  only ever be issued by network services and therefore issuance constraints are not relevant to end users.
  The ``TLS`` and ``WELL_KNOWN_LEGAL_IDENTITY`` roles must be issued by the ``NODE_CA`` certificate issued by the
  Doorman, and ``CONFIDENTIAL_IDENTITY`` certificates must be issued from a ``WELL_KNOWN_LEGAL_IDENTITY`` certificate.
  For a detailed specification of the extension please see :doc:`permissioning`.

* The network map service concept has been re-designed. More information can be found in :doc:`network-map`.

   * The previous design was never intended to be final but was rather a quick implementation in the earliest days of the
     Corda project to unblock higher priority items. It suffered from numerous disadvantages including lack of scalability,
     as one node was expected to hold open and manage connections to every node on the network; not reliable; hard to defend
     against DoS attacks; etc.

   * There is no longer a special network map node for distributing the network map to the other nodes. Instead the network
     map is now a collection of signed ``NodeInfo`` files distributed via HTTP.

   * The ``certificateSigningService`` config has been replaced by ``compatibilityZoneURL`` which is the base URL for the
     doorman registration and for downloading the network map. There is also an end-point for the node to publish its node-info
     object, which the node does each time it changes. ``networkMapService`` config has been removed.

   * To support local and test deployments, the node polls the ``additional-node-infos`` directory for these signed ``NodeInfo``
     objects which are stored in its local cache. On startup the node generates its own signed file with the filename format
     "nodeInfo-\*". This can be copied to every node's ``additional-node-infos`` directory that is part of the network.

   * Cordform (which is the ``deployNodes`` gradle task) does this copying automatically for the demos. The ``NetworkMap``
     parameter is no longer needed.

   * For test deployments we've introduced a bootstrapping tool (see :doc:`network-bootstrapper`).

   * ``extraAdvertisedServiceIds``, ``notaryNodeAddress``, ``notaryClusterAddresses`` and ``bftSMaRt`` configs have been
     removed. The configuration of notaries has been simplified into a single ``notary`` config object. See
     :doc:`corda-configuration-file` for more details.

   * Introducing the concept of network parameters which are a set of constants which all nodes on a network must agree on
     to correctly interoperate. These can be retrieved from ``ServiceHub.networkParameters``.

   * One of these parameters, ``maxTransactionSize``, limits the size of a transaction, including its attachments, so that
     all nodes have sufficient memory to validate transactions.

   * The set of valid notaries has been moved to the network parameters. Notaries are no longer identified by the CN in
     their X.500 name.

   * Single node notaries no longer have a second separate notary identity. Their main identity *is* their notary identity.
     Use ``NetworkMapCache.notaryIdentities`` to get the list of available notaries.

  * Added ``NetworkMapCache.getNodesByLegalName`` for querying nodes belonging to a distributed service such as a notary cluster
    where they all share a common identity. ``NetworkMapCache.getNodeByLegalName`` has been tightened to throw if more than
    one node with the legal name is found.

   * The common name in the node's X.500 legal name is no longer reserved and can be used as part of the node's name.

   * Moved ``NodeInfoSchema`` to internal package as the node info's database schema is not part of the public API. This
     was needed to allow changes to the schema.

* Support for external user credentials data source and password encryption [CORDA-827].

* Integrate database migration tool: http://www.liquibase.org/ :
 * The migration files are split per ``MappedSchemas``. (added new property: migrationResource used to point to the resource file containing the db changes corresponding to the JPA entities)
 * config flag ``database.initialiseSchema`` was renamed to: ``database.runMigration``  (if true then the migration is run during startup just before hibernate is initialised.)
 * config flag: ``database.serverNameTablePrefix`` was removed as we no longer use table prefixes
 * New command line argument:``—just-generate-db-migration outputSqlFile``: this will generate the delta from the last release, and will output the resulting sql into the outputSqlFile. It will not write to the db. It will not start the node!
 * New command line argument: ``--just-run-db-migration``: this will only run the db migration. It will not start the node!

* Exporting additional JMX metrics (artemis, hibernate statistics) and loading Jolokia agent at JVM startup when using
  DriverDSL and/or cordformation node runner.

* Removed confusing property ``database.initDatabase``, enabling its guarded behaviour with the dev-mode.
  In devMode Hibernate will try to create or update database schemas, otherwise it will expect relevant schemas to be present
  in the database (pre configured via DDL scripts or equivalent), and validate these are correct.

* ``ConfigUtilities`` now read system properties for a node. This allow to specify data source properties at runtime.

* ``AttachmentStorage`` now allows providing metadata on attachments upload - username and filename, currently as plain
  strings. Those can be then used for querying, utilizing ``queryAttachments`` method of the same interface.

* ``SSH Server`` - The node can now expose shell via SSH server with proper authorization and permissioning built in.

* ``CordaRPCOps`` implementation now checks permissions for any function invocation, rather than just when starting flows.

* ``wellKnownPartyFromAnonymous()`` now always resolve the key to a ``Party``, then the party to the well known party.
  Previously if it was passed a ``Party`` it would use its name as-is without verifying the key matched that name.

* ``OpaqueBytes.bytes`` now returns a clone of its underlying ``ByteArray``, and has been redeclared as ``final``.
  This is a minor change to the public API, but is required to ensure that classes like ``SecureHash`` are immutable.

* Experimental support for PostgreSQL: CashSelection done using window functions

* ``FlowLogic`` now exposes a series of function called ``receiveAll(...)`` allowing to join ``receive(...)`` instructions.

* Renamed "plugins" directory on nodes to "cordapps"

* The ``Cordformation`` gradle plugin has been split into ``cordformation`` and ``cordapp``. The former builds and
  deploys nodes for development and testing, the latter turns a project into a cordapp project that generates JARs in
  the standard CorDapp format.

* ``Cordapp`` now has a name field for identifying CorDapps and all CorDapp names are printed to console at startup.

* Enums now respect the whitelist applied to the Serializer factory serializing / deserializing them. If the enum isn't
  either annotated with the @CordaSerializable annotation or explicitly whitelisted then a NotSerializableException is
  thrown.

* Gradle task ``deployNodes`` can have an additional parameter ``configFile`` with the path to a properties file
  to be appended to node.conf.

* Cordformation node building DSL can have an additional parameter ``configFile`` with the path to a properties file
  to be appended to node.conf.

* ``FlowLogic`` now has a static method called ``sleep`` which can be used in certain circumstances to help with resolving
  contention over states in flows.  This should be used in place of any other sleep primitive since these are not compatible
  with flows and their use will be prevented at some point in the future.  Pay attention to the warnings and limitations
  described in the documentation for this method.  This helps resolve a bug in ``Cash`` coin selection.
  A new static property ``currentTopLevel`` returns the top most ``FlowLogic`` instance, or null if not in a flow.

* ``CordaService`` annotated classes should be upgraded to take a constructor parameter of type ``AppServiceHub`` which
  allows services to start flows marked with the ``StartableByService`` annotation. For backwards compatability
  service classes with only ``ServiceHub`` constructors will still work.

* ``TimeWindow`` now has a ``length`` property that returns the length of the time-window as a ``java.time.Duration`` object,
  or ``null`` if the time-window isn't closed.

* A new ``SIGNERS_GROUP`` with ordinal 6 has been added to ``ComponentGroupEnum`` that corresponds to the ``Command``
  signers.

* ``PartialMerkleTree`` is equipped with a ``leafIndex`` function that returns the index of a hash (leaf) in the
  partial Merkle tree structure.

* A new function ``checkCommandVisibility(publicKey: PublicKey)`` has been added to ``FilteredTransaction`` to check
  if every command that a signer should receive (e.g. an Oracle) is indeed visible.

* Changed the AMQP serializer to use the officially assigned R3 identifier rather than a placeholder.

* The ``ReceiveTransactionFlow`` can now be told to record the transaction at the same time as receiving it. Using this
  feature, better support for observer/regulator nodes has been added. See :doc:`tutorial-observer-nodes`.

* Added an overload of ``TransactionWithSignatures.verifySignaturesExcept`` which takes in a collection of ``PublicKey`` s.

* ``DriverDSLExposedInterface`` has been renamed to ``DriverDSL`` and the ``waitForAllNodesToFinish()`` method has instead
  become a parameter on driver creation.

* Values for the ``database.transactionIsolationLevel`` config now follow the ``java.sql.Connection`` int constants but
  without the "TRANSACTION" prefix, i.e. "NONE", "READ_UNCOMMITTED", etc.

* Peer-to-peer communications is now via AMQP 1.0 as default.
  Although the legacy Artemis CORE bridging can still be used by setting the ``useAMQPBridges`` configuration property to false.

* The Artemis topics used for peer-to-peer communication have been changed to be more consistent with future cryptographic
  agility and to open up the future possibility of sharing brokers between nodes. This is a breaking wire level change
  as it means that nodes after this change will not be able to communicate correctly with nodes running the previous version.
  Also, any pending enqueued messages in the Artemis message store will not be delivered correctly to their original target.
  However, assuming a clean reset of the artemis data and that the nodes are consistent versions,
  data persisted via the AMQP serializer will be forward compatible.

* The ability for CordaServices to register callbacks so they can be notified of shutdown and clean up resource such as
  open ports.

* Enterprise Corda only: Compatibility with SQL Server 2017 and SQL Azure databases.

* Enterprise Corda only: node configuration property ``database.schema`` and documented existing database properties.

* Enterprise Corda only: Compatibility with PostgreSQL 9.6 database.

* Enterprise Corda only: Compatibility with Oracle 11g RC2 and 12c database.

* Move to a message based control of peer to peer bridge formation to allow for future out of process bridging components.
  This removes the legacy Artemis bridges completely, so the ``useAMQPBridges`` configuration property has been removed.

* A ``CordaInternal`` attribute has been added to identify properties that are not intended to form part of the
  public api and as such are not intended for public use. This is alongside the existing ``DoNotImplement`` attribute for classes which
  provide Corda functionality to user applications, but should not be implemented by consumers, and any classes which
  are defined in ``.internal`` packages, which are also not for public use.

* Marked ``stateMachine`` on ``FlowLogic`` as ``CordaInternal`` to make clear that is it not part of the public api and is
  only for internal use

* Provided experimental support for specifying your own webserver to be used instead of the default development
  webserver in ``Cordform`` using the ``webserverJar`` argument

* Created new ``StartedMockNode`` and ``UnstartedMockNode`` classes which  are wrappers around our MockNode implementation
  that expose relevant methods for testing without exposing internals, create these using a ``MockNetwork``.

* The test utils in ``Expect.kt``, ``SerializationTestHelpers.kt``, ``TestConstants.kt`` and ``TestUtils.kt`` have moved
  from the ``net.corda.testing`` package to the ``net.corda.testing.core`` package, and ``FlowStackSnapshot.kt`` has moved to the
  ``net.corda.testing.services`` package. Moving existing classes out of the ``net.corda.testing.*`` package
  will help make it clearer which parts of the api are stable. Scripts have been provided to smooth the upgrade
  process for existing projects in the ``tools\scripts`` directory of the Corda repo.

* ``TransactionSignature`` includes a new ``partialMerkleTree`` property, required for future support of signing over
  multiple transactions at once.

* Shell (embedded available only in dev mode or via SSH) connects to the node via RPC instead of using the ``CordaRPCOps`` object directly.
  To enable RPC connectivity ensure node’s ``rpcSettings.address`` and ``rpcSettings.adminAddress`` settings are present.
