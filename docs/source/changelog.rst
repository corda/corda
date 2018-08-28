Changelog
=========

Here's a summary of what's changed in each Corda release. For guidance on how to upgrade code from the previous
release, see :doc:`upgrade-notes`.

Unreleased
----------
* Removed experimental feature `CordformDefinition`

* Vault query fix: support query by parent classes of Contract State classes (see https://github.com/corda/corda/issues/3714)

* Added ``registerResponderFlow`` method to ``StartedMockNode``, to support isolated testing of responder flow behaviour.

* "app", "rpc", "p2p" and "unknown" are no longer allowed as uploader values when importing attachments. These are used
  internally in security sensitive code.

* Introduced ``TestCorDapp`` and utilities to support asymmetric setups for nodes through ``DriverDSL``, ``MockNetwork`` and ``MockServices``.

* Change type of the `checkpoint_value` column. Please check the upgrade-notes on how to update your database.

* Removed buggy :serverNameTablePrefix: configuration.

* ``freeLocalHostAndPort``, ``freePort``, and ``getFreeLocalPorts`` from ``TestUtils`` have been deprecated as they
  don't provide any guarantee the returned port will be available which can result in flaky tests. Use ``PortAllocation.Incremental``
  instead.

* The ``corda-bridgserver.jar`` has been renamed to ``corda-firewall.jar`` to be more consistent
  with marketing materials and purpose of the jar. Further to this we have also renamed ``bridge.conf`` to ``firewall.conf``
  and within that file the ``bridgeMode`` propety has been modified to ``firewallMode`` for overall consistency.
  This will be a breaking change for early adopters and their deployments, but hopefully will be more future proof.

* Docs for IdentityService. assertOwnership updated to correctly state that an UnknownAnonymousPartyException is thrown
  rather than IllegalStateException.

* The Corda JPA entities no longer implement java.io.Serializable, as this was causing persistence errors in obscure cases.
  Java serialization is disabled globally in the node, but in the unlikely event you were relying on these types being Java serializable please contact us.

* Remove all references to the out-of-process transaction verification.

* Introduced a hierarchy of ``DatabaseMigrationException``s, allowing ``NodeStartup`` to gracefully inform users of problems related to database migrations before exiting with a non-zero code.

* The class carpenter has a "lenient" mode where it will, during deserialisation, happily synthesis classes that implement
  interfaces that will have unimplemented methods. This is useful, for example, for object viewers. This can be turned on
  with ``SerializationContext.withLenientCarpenter``.

* Introduced a grace period before the initial node registration fails if the node cannot connect to the Doorman.
  It retries 10 times with a 1 minute interval in between each try. At the moment this is not configurable.

* Added a ``FlowMonitor`` to log information about flows that have been waiting for IO more than a configurable threshold.

* H2 database changes:
  * The node's H2 database now listens on ``localhost`` by default.
  * The database server address must also be enabled in the node configuration.
  * A new ``h2Settings`` configuration block supersedes the ``h2Port`` option.

* Improved documentation PDF quality. Building the documentation now requires ``LaTex`` to be installed on the OS.

* Add ``devModeOptions.allowCompatibilityZone`` to re-enable the use of a compatibility zone and ``devMode``

* Fixed an issue where ``trackBy`` was returning ``ContractStates`` from a transaction that were not being tracked. The
  unrelated ``ContractStates`` will now be filtered out from the returned ``Vault.Update``.

* Introducing the flow hospital - a component of the node that manages flows that have errored and whether they should
  be retried from their previous checkpoints or have their errors propagate. Currently it will respond to any error that
  occurs during the resolution of a received transaction as part of ``FinalityFlow``. In such a scenario the receiving
  flow will be parked and retried on node restart. This is to allow the node operator to rectify the situation as otherwise
  the node will have an incomplete view of the ledger.

* Fixed an issue preventing out of process nodes started by the ``Driver`` from logging to file.

* Fixed an issue with ``CashException`` not being able to deserialize after the introduction of AMQP for RPC.

* Removed -Xmx VM argument from Explorer's Capsule setup. This helps avoiding out of memory errors.

* New ``killFlow`` RPC for killing stuck flows.

* Shell now kills an ongoing flow when CTRL+C is pressed in the terminal.

* Add check at startup that all persisted Checkpoints are compatible with the current version of the code.

* ``ServiceHub`` and ``CordaRPCOps`` can now safely be used from multiple threads without incurring in database transaction problems.

* Doorman and NetworkMap url's can now be configured individually rather than being assumed to be
  the same server. Current ``compatibilityZoneURL`` configurations remain valid. See both :doc:`corda-configuration-file`
  and :doc:`permissioning` for details.

* Improved audit trail for ``FinalityFlow`` and related sub-flows.

* Notary client flow retry logic was improved to handle validating flows better. Instead of re-sending flow messages the
  entire flow is now restarted after a timeout. The relevant node configuration section was renamed from ``p2pMessagingRetry``,
  to ``flowTimeout`` to reflect the behaviour change.

* The node's configuration is only printed on startup if ``devMode`` is ``true``, avoiding the risk of printing passwords
  in a production setup.

* ``NodeStartup`` will now only print node's configuration if ``devMode`` is ``true``, avoiding the risk of printing passwords in a production setup.

* SLF4J's MDC will now only be printed to the console if not empty. No more log lines ending with "{}".

* ``WireTransaction.Companion.createComponentGroups`` has been marked as ``@CordaInternal``. It was never intended to be
  public and was already internal for Kotlin code.

* RPC server will now mask internal errors to RPC clients if not in devMode. ``Throwable``s implementing ``ClientRelevantError`` will continue to be propagated to clients.

* RPC Framework moved from Kryo to the Corda AMQP implementation [Corda-847]. This completes the removal
  of ``Kryo`` from general use within Corda, remaining only for use in flow checkpointing.

* Set co.paralleluniverse.fibers.verifyInstrumentation=true in devMode.

* Node will now gracefully fail to start if one of the required ports is already in use.

* Node will now gracefully fail to start if ``devMode`` is true and ``compatibilityZoneURL`` is specified.

* Added smart detection logic for the development mode setting and an option to override it from the command line.

* Changes to the JSON/YAML serialisation format from ``JacksonSupport``, which also applies to the node shell:

  * ``Instant`` and ``Date`` objects are serialised as ISO-8601 formatted strings rather than timestamps
  * ``PublicKey`` objects are serialised and looked up according to their Base58 encoded string
  * ``Party`` objects can be deserialised by looking up their public key, in addition to their name
  * ``NodeInfo`` objects are serialised as an object and can be looked up using the same mechanism as ``Party``
  * ``NetworkHostAndPort`` serialised according to its ``toString()``
  * ``PartyAndCertificate`` is serialised as the name
  * ``SerializedBytes`` is serialised by materialising the bytes into the object it represents, and then serialising that
    object into YAML/JSON
  * ``X509Certificate`` is serialised as an object with key fields such as ``issuer``, ``publicKey``, ``serialNumber``, etc.
    The encoded bytes are also serialised into the ``encoded`` field. This can be used to deserialize an ``X509Certificate``
    back.
  * ``CertPath`` objects are serialised as a list of ``X509Certificate`` objects.
  * ``WireTransaction`` now nicely outputs into its components: ``id``, ``notary``, ``inputs``, ``attachments``, ``outputs``,
    ``commands``, ``timeWindow`` and ``privacySalt``. This can be deserialized back.
  * ``SignedTransaction`` is serialised into ``wire`` (i.e. currently only ``WireTransaction`` tested) and ``signatures``,
    and can be deserialized back.

* ``fullParties`` boolean parameter added to ``JacksonSupport.createDefaultMapper`` and ``createNonRpcMapper``. If ``true``
  then ``Party`` objects are serialised as JSON objects with the ``name`` and ``owningKey`` fields. For ``PartyAndCertificate``
  the ``certPath`` is serialised.

* Several members of ``JacksonSupport`` have been deprecated to highlight that they are internal and not to be used.

* The Vault Criteria API has been extended to take a more precise specification of which class contains a field. This
  primarily impacts Java users; Kotlin users need take no action. The old methods have been deprecated but still work -
  the new methods avoid bugs that can occur when JPA schemas inherit from each other.

* Due to ongoing work the experimental interfaces for defining custom notary services have been moved to the internal package.
  CorDapps implementing custom notary services will need to be updated, see ``samples/notary-demo`` for an example.
  Further changes may be required in the future.

* Configuration file changes:

  * Added program line argument ``on-unknown-config-keys`` to allow specifying behaviour on unknown node configuration property keys.
    Values are: [FAIL, WARN, IGNORE], default to FAIL if unspecified.
  * Introduced a placeholder for custom properties within ``node.conf``; the property key is "custom".
  * The deprecated web server now has its own ``web-server.conf`` file, separate from ``node.conf``.
  * Property keys with double quotes (e.g. `"key"`) in ``node.conf`` are no longer allowed, for rationale refer to :doc:`corda-configuration-file`.

* Added public support for creating ``CordaRPCClient`` using SSL. For this to work the node needs to provide client applications
  a certificate to be added to a truststore. See :doc:`tutorial-clientrpc-api`

* The node RPC broker opens 2 endpoints that are configured with ``address`` and ``adminAddress``. RPC Clients would connect to the address, while the node will connect
  to the adminAddress. Previously if ssl was enabled for RPC the ``adminAddress`` was equal to ``address``.

* Upgraded H2 to v1.4.197

* Shell (embedded available only in dev mode or via SSH) connects to the node via RPC instead of using the ``CordaRPCOps`` object directly.
  To enable RPC connectivity ensure node’s ``rpcSettings.address`` and ``rpcSettings.adminAddress`` settings are present.

* Changes to the network bootstrapper:

  * The whitelist.txt file is no longer needed. The existing network parameters file is used to update the current contracts
    whitelist.
  * The CorDapp jars are also copied to each nodes' `cordapps` directory.

* Errors thrown by a Corda node will now reported to a calling RPC client with attention to serialization and obfuscation of internal data.

* Serializing an inner class (non-static nested class in Java, inner class in Kotlin) will be rejected explicitly by the serialization
  framework. Prior to this change it didn't work, but the error thrown was opaque (complaining about too few arguments
  to a constructor). Whilst this was possible in the older Kryo implementation (Kryo passing null as the synthesised
  reference to the outer class) as per the Java documentation `here <https://docs.oracle.com/javase/tutorial/java/javaOO/nested.html>`_
  we are disallowing this as the paradigm in general makes little sense for contract states.

* API change: ``net.corda.core.schemas.PersistentStateRef`` fields (index and txId) are now non-nullable.
  The fields were always effectively non-nullable - values were set from non-nullable fields of other objects.
  The class is used as database Primary Key columns of other entities and databases already impose those columns as non-nullable
  (even if JPA annotation nullable=false was absent).
  In case your Cordapps use this entity class to persist data in own custom tables as non Primary Key columns refer to :doc:`upgrade-notes` for upgrade instructions.

* Adding a public method to check if a public key satisfies Corda recommended algorithm specs, `Crypto.validatePublicKey(java.security.PublicKey)`.
  For instance, this method will check if an ECC key lies on a valid curve or if an RSA key is >= 2048bits. This might
  be required for extra key validation checks, e.g., for Doorman to check that a CSR key meets the minimum security requirements.

* Table name with a typo changed from ``NODE_ATTCHMENTS_CONTRACTS`` to ``NODE_ATTACHMENTS_CONTRACTS``.

* Node logs a warning for any ``MappedSchema`` containing a JPA entity referencing another JPA entity from a different ``MappedSchema`.
  The log entry starts with `Cross-reference between MappedSchemas.`.
  API: Persistence documentation no longer suggests mapping between different schemas.

* Upgraded Artemis to v2.6.2.

* Introduced the concept of "reference input states". A reference input state is a ``ContractState`` which can be referred
  to in a transaction by the contracts of input and output states but whose contract is not executed as part of the
  transaction verification process and is not consumed when the transaction is committed to the ledger but is checked
  for "current-ness". In other words, the contract logic isn't run for the referencing transaction only. It's still a
  normal state when it occurs in an input or output position. *This feature is only available on Corda networks running
  with a minimum platform version of 4.*

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

* Introduced Flow Draining mode, in which a node continues executing existing flows, but does not start new. This is to support graceful node shutdown/restarts.
  In particular, when this mode is on, new flows through RPC will be rejected, scheduled flows will be ignored, and initial session messages will not be consumed.
  This will ensure that the number of checkpoints will strictly diminish with time, allowing for a clean shutdown.

* Make the serialisation finger-printer a pluggable entity rather than hard wiring into the factory

* Removed blacklisted word checks in Corda X.500 name to allow "Server" or "Node" to be use as part of the legal name.

* Separated our pre-existing Artemis broker into an RPC broker and a P2P broker.

* Refactored ``NodeConfiguration`` to expose ``NodeRpcOptions`` (using top-level "rpcAddress" property still works with warning).

* Modified ``CordaRPCClient`` constructor to take a ``SSLConfiguration?`` additional parameter, defaulted to ``null``.

* Introduced ``CertificateChainCheckPolicy.UsernameMustMatchCommonName`` sub-type, allowing customers to optionally enforce username == CN condition on RPC SSL certificates.

* Modified ``DriverDSL`` and sub-types to allow specifying RPC settings for the Node.

* Modified the ``DriverDSL`` to start Cordformation nodes allowing automatic generation of "rpcSettings.adminAddress" in case "rcpSettings.useSsl" is ``false`` (the default).

* Introduced ``UnsafeCertificatesFactory`` allowing programmatic generation of X509 certificates for test purposes.

* JPA Mapping annotations for States extending ``CommonSchemaV1.LinearState`` and ``CommonSchemaV1.FungibleState`` on the
  `participants` collection need to be moved to the actual class. This allows to properly specify the unique table name per a collection.
  See: DummyDealStateSchemaV1.PersistentDummyDealState

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

* Shell (embedded available only in dev mode or via SSH) connects to the node via RPC instead of using the ``CordaRPCOps`` object directly.
  To enable RPC connectivity ensure node’s ``rpcSettings.address`` and ``rpcSettings.adminAddress`` settings are present.

.. _changelog_v2:

Corda 2.0
---------

* ``OpaqueBytes.bytes`` now returns a clone of its underlying ``ByteArray``, and has been redeclared as ``final``.
  This is a minor change to the public API, but is required to ensure that classes like ``SecureHash`` are immutable.

* ``FlowLogic`` now has a static method called ``sleep`` which can be used in certain circumstances to help with resolving
  contention over states in flows.  This should be used in place of any other sleep primitive since these are not compatible
  with flows and their use will be prevented at some point in the future.  Pay attention to the warnings and limitations
  described in the documentation for this method.  This helps resolve a bug in ``Cash`` coin selection.
  A new static property `currentTopLevel` returns the top most `FlowLogic` instance, or null if not in a flow.

* Updating Jolokia dependency to latest version (includes security fixes)

.. _changelog_v1:

Corda 1.0
---------

* Unification of VaultQuery And VaultService APIs
  Developers now only need to work with a single Vault Service API for all needs.

* Java 8 lambdas now work property with Kryo during check-pointing.

* Java 8 serializable lambdas now work property with Kryo during check-pointing.

* String constants have been marked as ``const`` type in Kotlin, eliminating cases where functions of the form
  ``get<constant name>()`` were created for the Java API. These can now be referenced by their name directly.

* ``FlowLogic`` communication has been extensively rewritten to use functions on ``FlowSession`` as the base for communication
  between nodes.

  * Calls to ``send()``, ``receive()`` and ``sendAndReceive()`` on FlowLogic should be replaced with calls
    to the function of the same name on ``FlowSession``. Note that the replacement functions do not take in a destination
    parameter, as this is defined in the session.
  * Initiated flows now take in a ``FlowSession`` instead of ``Party`` in their constructor. If you need to access the
    counterparty identity, it is in the ``counterparty`` property of the flow session.


* Added X509EdDSAEngine to intercept and rewrite EdDSA public keys wrapped in X509Key instances. This corrects an issue
  with verifying certificate paths loaded from a Java Keystore where they contain EdDSA keys.

* Confidential identities are now complete:

   * The identity negotiation flow is now called ``SwapIdentitiesFlow``, renamed from ``TransactionKeyFlow``.
   * generateSpend() now creates a new confidential identity for the change address rather than using the identity of the
     input state owner.
   * Please see the documentation :doc:`key-concepts-identity` and :doc:`api-identity` for more details.

* Remove the legacy web front end from the SIMM demo.

* ``NodeInfo`` and ``NetworkMapCache`` changes:

   * Removed ``NodeInfo::legalIdentity`` in preparation for handling of multiple identities. We left list of ``NodeInfo::legalIdentitiesAndCerts``,
     the first identity still plays a special role of main node identity.
   * We no longer support advertising services in network map. Removed ``NodeInfo::advertisedServices``, ``serviceIdentities``
     and ``notaryIdentity``.
   * Removed service methods from ``NetworkMapCache``: ``partyNodes``, ``networkMapNodes``, ``notaryNodes``, ``regulatorNodes``,
     ``getNodesWithService``, ``getPeersWithService``, ``getRecommended``, ``getNodesByAdvertisedServiceIdentityKey``, ``getAnyNotary``,
     ``notaryNode``, ``getAnyServiceOfType``. To get all known ``NodeInfo``'s call ``allNodes``.
   * In preparation for ``NetworkMapService`` redesign and distributing notaries through ``NetworkParameters`` we added
     ``NetworkMapCache::notaryIdentities`` list to enable to lookup for notary parties known to the network. Related ``CordaRPCOps::notaryIdentities``
     was introduced. Other special nodes parties like Oracles or Regulators need to be specified directly in CorDapp or flow.
   * Moved ``ServiceType`` and ``ServiceInfo`` to ``net.corda.nodeapi`` package as services are only required on node startup.

* Adding enum support to the class carpenter

* ``ContractState::contract`` has been moved ``TransactionState::contract`` and it's type has changed to ``String`` in order to
  support dynamic classloading of contract and contract constraints.

* CorDapps that contain contracts are now automatically loaded into the attachment storage - for CorDapp developers this
  now means that contracts should be stored in separate JARs to flows, services and utilities to avoid large JARs being
  auto imported to the attachment store.

* About half of the code in test-utils has been moved to a new module ``node-driver``,
  and the test scope modules are now located in a ``testing`` directory.

* ``CordaPluginRegistry`` has been renamed to ``SerializationWhitelist`` and moved to the ``net.corda.core.serialization``
  package. The API for whitelisting types that can't be annotated was slightly simplified. This class used to contain
  many things, but as we switched to annotations and classpath scanning over time it hollowed out until this was
  the only functionality left.  You also need to rename your services resource file to the new class name.
  An associated property on ``MockNode`` was renamed from ``testPluginRegistries`` to ``testSerializationWhitelists``.

* Contract Upgrades: deprecated RPC authorization / deauthorization API calls in favour of equivalent flows in ContractUpgradeFlow.
  Implemented contract upgrade persistence using JDBC backed persistent map.

* Vault query common attributes (state status and contract state types) are now handled correctly when using composite
  criteria specifications. State status is overridable. Contract states types are aggregatable.

* Cash selection algorithm is now pluggable (with H2 being the default implementation)

* Removed usage of Requery ORM library (replaced with JPA/Hibernate)

* Vault Query performance improvement (replaced expensive per query SQL statement to obtain concrete state types
  with single query on start-up followed by dynamic updates using vault state observable))

* Vault Query fix: filter by multiple issuer names in ``FungibleAssetQueryCriteria``

* Following deprecated methods have been removed:

  * In ``DataFeed``

    * ``first`` and ``current``, replaced by ``snapshot``
    * ``second`` and ``future``, replaced by ``updates``

  * In ``CordaRPCOps``

    * ``stateMachinesAndUpdates``, replaced by ``stateMachinesFeed``
    * ``verifiedTransactions``, replaced by ``verifiedTransactionsFeed``
    * ``stateMachineRecordedTransactionMapping``, replaced by ``stateMachineRecordedTransactionMappingFeed``
    * ``networkMapUpdates``, replaced by ``networkMapFeed``

* Due to security concerns and the need to remove the concept of state relevancy (which isn't needed in Corda),
  ``ResolveTransactionsFlow`` has been made internal. Instead merge the receipt of the ``SignedTransaction`` and the subsequent
  sub-flow call to ``ResolveTransactionsFlow`` with a single call to ``ReceiveTransactionFlow``. The flow running on the counterparty
  must use ``SendTransactionFlow`` at the correct place. There is also ``ReceiveStateAndRefFlow`` and ``SendStateAndRefFlow`` for
  dealing with ``StateAndRef``'s.

* Vault query soft locking enhancements and deprecations

  * removed original ``VaultService`` ``softLockedStates`` query mechanism.
  * introduced improved ``SoftLockingCondition`` filterable attribute in ``VaultQueryCriteria`` to enable specification of different soft locking retrieval behaviours (exclusive of soft locked states, soft locked states only, specified by set of lock ids)

* Trader demo now issues cash and commercial paper directly from the bank node, rather than the seller node self-issuing
  commercial paper but labelling it as if issued by the bank.

* Merged handling of well known and confidential identities in the identity service. Registration now takes in an identity
  (either type) plus supporting certificate path, and de-anonymisation simply returns the issuing identity where known.
  If you specifically need well known identities, use the network map, which is the authoritative source of current well
  known identities.

* Currency-related API in ``net.corda.core.contracts.ContractsDSL`` has moved to ```net.corda.finance.CurrencyUtils``.

* Remove `IssuerFlow` as it allowed nodes to request arbitrary amounts of cash to be issued from any remote node. Use
  `CashIssueFlow` instead.

* Some utility/extension functions (``sumOrThrow``, ``sumOrNull``, ``sumOrZero`` on ``Amount`` and ``Commodity``)
  have moved to be static methods on the classes themselves. This improves the API for Java users who no longer
  have to see or known about file-level FooKt style classes generated by the Kotlin compile, but means that IntelliJ
  no longer auto-suggests these extension functions in completion unless you add import lines for them yourself
  (this is Kotlin IDE bug KT-15286).

* ``:finance`` module now acting as a CorDapp with regard to flow registration, schemas and serializable types.

* ``WebServerPluginRegistry`` now has a ``customizeJSONSerialization`` which can be overridden to extend the REST JSON
  serializers. In particular the IRS demos must now register the ``BusinessCalendar`` serializers.

* Moved ``:finance`` gradle project files into a ``net.corda.finance`` package namespace.
  This may require adjusting imports of Cash flow references and also of ``StartFlow`` permission in ``gradle.build`` files.

* Removed the concept of relevancy from ``LinearState``. The ``ContractState``'s relevancy to the vault can be determined
  by the flow context, the vault will process any transaction from a flow which is not derived from transaction resolution verification.

* Removed the tolerance attribute from ``TimeWindowChecker`` and thus, there is no extra tolerance on the notary side anymore.

* The ``FungibleAsset`` interface has been made simpler. The ``Commands`` grouping interface
  that included the ``Move``, ``Issue`` and ``Exit`` interfaces have all been removed, while the ``move`` function has
  been renamed to ``withNewOwnerAndAmount`` to be consistent with the ``withNewOwner`` function of the ``OwnableState``.

* The ``IssueCommand`` interface has been removed from ``Structures``, because, due to the introduction of nonces per
  transaction component, the issue command does not need a nonce anymore and it does not require any other attributes.

* As a consequence of the above and the simpler ``FungibleAsset`` format, fungible assets like ``Cash`` now use
  ``class Issue : TypeOnlyCommandData()``, because it's only its presence (``Issue``) that matters.

* A new `PrivacySalt` transaction component is introduced, which is now an attribute in ``TraversableTransaction`` and
  inherently in ``WireTransaction``.

* A new ``nonces: List<SecureHash>`` feature has been added to ``FilteredLeaves``.

* Due to the ``nonces`` and ``PrivacySalt`` introduction, new functions have been added to ``MerkleTransaction``:
  ``fun <T : Any> serializedHash(x: T, privacySalt: PrivacySalt?, index: Int): SecureHash``
  ``fun <T : Any> serializedHash(x: T, nonce: SecureHash): SecureHash``
  ``fun computeNonce(privacySalt: PrivacySalt, index: Int)``.

* A new ``SignatureMetadata`` data class is introduced with two attributes, ``platformVersion: Int`` and
  ``schemeNumberID: Int`` (the signature scheme used).

* As part of the metadata support in signatures, a new ``data class SignableData(val txId: SecureHash, val signatureMetadata: SignatureMetadata)``
  is introduced, which represents the object actually signed.

* The unused ``MetaData`` and ``SignatureType`` in ``crypto`` package have been removed.

* The ``class TransactionSignature(bytes: ByteArray, val by: PublicKey, val signatureMetadata:``
  ``SignatureMetadata): DigitalSignature(bytes)`` class is now utilised Vs the old ``DigitalSignature.WithKey`` for
  Corda transaction signatures. Practically, it takes the ``signatureMetadata`` as an extra input, in order to support
  signing both the transaction and the extra metadata.

* To reflect changes in the signing process, the ``Crypto`` object is now equipped with the:
  ``fun doSign(keyPair: KeyPair, signableData: SignableData): TransactionSignature`` and
  ``fun doVerify(txId: SecureHash, transactionSignature: TransactionSignature): Boolean`` functions.

* ``SerializationCustomization.addToWhitelist()`` now accepts multiple classes via varargs.

* Two functions to easily sign a ``FilteredTransaction`` have been added to ``ServiceHub``:
  ``createSignature(filteredTransaction: FilteredTransaction, publicKey: PublicKey)`` and
  ``createSignature(filteredTransaction: FilteredTransaction)`` to sign with the legal identity key.

* A new helper method ``buildFilteredTransaction(filtering: Predicate<Any>)`` is added to ``SignedTransaction`` to
  directly build a ``FilteredTransaction`` using provided filtering functions, without first accessing the
  ``tx: WireTransaction``.

* Test type ``NodeHandle`` now has method ``stop(): CordaFuture<Unit>`` that terminates the referenced node.

* Fixed some issues in IRS demo:
   * Fixed leg and floating leg notional amounts were not displayed for created deals neither in single nor in list view.
   * Parties were not displayed for created deals in single view.
   * Non-default notional amounts caused the creation of new deals to fail.

.. warning:: Renamed configuration property key `basedir` to `baseDirectory`. This will require updating existing configuration files.

* Removed deprecated parts of the API.

* Removed ``PluginServiceHub``. Replace with ``ServiceHub`` for ``@CordaService`` constructors.

* ``X509CertificateHolder`` has been removed from the public API, replaced by ``java.security.X509Certificate``.

* Moved ``CityDatabase`` out of ``core`` and into ``finance``

* All of the ``serializedHash`` and ``computeNonce`` functions have been removed from ``MerkleTransaction``.
  The ``serializedHash(x: T)`` and ``computeNonce`` were moved to ``CryptoUtils``.

* Two overloaded methods ``componentHash(opaqueBytes: OpaqueBytes, privacySalt: PrivacySalt,``
  ``componentGroupIndex: Int, internalIndex: Int): SecureHash`` and ``componentHash(nonce: SecureHash, opaqueBytes: OpaqueBytes): SecureHash`` have
  been added to ``CryptoUtils``. Similarly to ``computeNonce``, they internally use SHA256d for nonce and leaf hash
  computations.

* The ``verify(node: PartialTree, usedHashes: MutableList<SecureHash>): SecureHash`` in ``PartialMerkleTree`` has been
  renamed to ``rootAndUsedHashes`` and is now public, as it is required in the verify function of ``FilteredTransaction``.

* ``TraversableTransaction`` is now an abstract class extending ``CoreTransaction``. ``WireTransaction`` and
  ``FilteredTransaction`` now extend ``TraversableTransaction``.

* Two classes, ``ComponentGroup(open val groupIndex: Int, open val components: List<OpaqueBytes>)`` and
  ``FilteredComponentGroup(override val groupIndex: Int, override val components:``
  ``List<OpaqueBytes>, val nonces: List<SecureHash>, val partialMerkleTree:``
  ``PartialMerkleTree): ComponentGroup(groupIndex, components)`` have been added, which are properties
  of the ``WireTransaction`` and ``FilteredTransaction``, respectively.

* ``checkAllComponentsVisible(componentGroupEnum: ComponentGroupEnum)`` is added to ``FilteredTransaction``, a new
  function to check if all components are visible in a specific component-group.

* To allow for backwards compatibility, ``WireTransaction`` and ``FilteredTransaction`` have new fields and
  constructors: ``WireTransaction(componentGroups: List<ComponentGroup>, privacySalt: PrivacySalt = PrivacySalt())``,
  ``FilteredTransaction private constructor(id: SecureHash,filteredComponentGroups:``
  ``List<FilteredComponentGroup>, groupHashes: List<SecureHash>``. ``FilteredTransaction`` is still built via
  ``buildFilteredTransaction(wtx: WireTransaction, filtering: Predicate<Any>)``.

* ``FilteredLeaves`` class have been removed and as a result we can directly call the components from
  ``FilteredTransaction``, such as ``ftx.inputs`` Vs the old ``ftx.filteredLeaves.inputs``.

* A new ``ComponentGroupEnum`` is added with the following enum items: ``INPUTS_GROUP``, ``OUTPUTS_GROUP``,
  ``COMMANDS_GROUP``, ``ATTACHMENTS_GROUP``, ``NOTARY_GROUP``, ``TIMEWINDOW_GROUP``.

* ``ContractUpgradeFlow.Initiator`` has been renamed to ``ContractUpgradeFlow.Initiate``

* ``@RPCSinceVersion``, ``RPCException`` and ``PermissionException`` have moved to ``net.corda.client.rpc``.

* Current implementation of SSL in ``CordaRPCClient`` has been removed until we have a better solution which doesn't rely
  on the node's keystore.
