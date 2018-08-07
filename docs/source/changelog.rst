Changelog
=========

Here's a summary of what's changed in each Corda release. For guidance on how to upgrade code from the previous
release, see :doc:`upgrade-notes`.

Unreleased
----------
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

* Docs for IdentityService. assertOwnership updated to correctly state that an UnknownAnonymousPartyException is thrown
  rather than IllegalStateException.

* The Corda JPA entities no longer implement java.io.Serializable, as this was causing persistence errors in obscure cases.
  Java serialization is disabled globally in the node, but in the unlikely event you were relying on these types being Java serializable please contact us.

* Remove all references to the out-of-process transaction verification.

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

* Node can be shut down abruptly by ``shutdown`` function in `CordaRPCOps` or gracefully (draining flows first) through ``gracefulShutdown`` command from shell.

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

* Updated the api scanner gradle plugin to work the same way as the version in master. These changes make the api scanner more
  accurate and fix a couple of bugs, and change the format of the api-current.txt file slightly. Backporting these changes
  to the v3 branch will make it easier for us to ensure that apis are stable for future versions. These changes are
  released in gradle plugins version 3.0.10. For more information on the api scanner see
  the `documentation <https://github.com/corda/corda-gradle-plugins/tree/master/api-scanner>`_.

* Fixed security vulnerability when using the ``HashAttachmentConstraint``. Added strict check that the contract JARs
  referenced in a transaction were deployed on the node.

* Fixed node's behaviour on startup when there is no connectivity to network map. Node continues to work normally if it has
  all the needed network data, waiting in the background for network map to become available.

.. _changelog_v3:

Version 3.0
-----------

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

* X.509 certificates now have an extension that specifies the Corda role the certificate is used for, and the role
  hierarchy is now enforced in the validation code. See ``net.corda.core.internal.CertRole`` for the current implementation
  until final documentation is prepared. Certificates at ``NODE_CA``, ``WELL_KNOWN_SERVICE_IDENTITY`` and above must
  only ever by issued by network services and therefore issuance constraints are not relevant to end users.
  The ``TLS``, ``WELL_KNOWN_LEGAL_IDENTITY`` roles must be issued by the ``NODE_CA`` certificate issued by the
  Doorman, and ``CONFIDENTIAL_IDENTITY`` certificates must be issued from a ``WELL_KNOWN_LEGAL_IDENTITY`` certificate.
  For a detailed specification of the extension please see :doc:`permissioning`.

* The network map service concept has been re-designed. More information can be found in :doc:`network-map`.

   * The previous design was never intended to be final but was rather a quick implementation in the earliest days of the
     Corda project to unblock higher priority items. It suffers from numerous disadvantages including lack of scalability,
     as one node is expected to hold open and manage connections to every node on the network; not reliable; hard to defend
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
     their X500 name.

   * Single node notaries no longer have a second separate notary identity. Their main identity *is* their notary identity.
     Use ``NetworkMapCache.notaryIdentities`` to get the list of available notaries.

  * Added ``NetworkMapCache.getNodesByLegalName`` for querying nodes belonging to a distributed service such as a notary cluster
    where they all share a common identity. ``NetworkMapCache.getNodeByLegalName`` has been tightened to throw if more than
    one node with the legal name is found.

   * The common name in the node's X500 legal name is no longer reserved and can be used as part of the node's name.

   * Moved ``NodeInfoSchema`` to internal package as the node info's database schema is not part of the public API. This
     was needed to allow changes to the schema.

* Support for external user credentials data source and password encryption [CORDA-827].

* Exporting additional JMX metrics (artemis, hibernate statistics) and loading Jolokia agent at JVM startup when using
  DriverDSL and/or cordformation node runner.

* Removed confusing property database.initDatabase, enabling its guarded behaviour with the dev-mode.
  In devMode Hibernate will try to create or update database schemas, otherwise it will expect relevant schemas to be present
  in the database (pre configured via DDL scripts or equivalent), and validate these are correct.

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

* Updating Jolokia dependency to latest version (includes security fixes)

.. _changelog_v1:

Release 1.0
-----------

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

.. _changelog_m14:

Milestone 14
------------

* Changes in ``NodeInfo``:

   * ``PhysicalLocation`` was renamed to ``WorldMapLocation`` to emphasise that it doesn't need to map to a truly physical
     location of the node server.
   * Slots for multiple IP addresses and ``legalIdentitiesAndCert`` entries were introduced. Addresses are no longer of type
     ``SingleMessageRecipient``, but of ``NetworkHostAndPort``.

* ``ServiceHub.storageService`` has been removed. ``attachments`` and ``validatedTransactions`` are now direct members of
  ``ServiceHub``.

* Mock identity constants used in tests, such as ``ALICE``, ``BOB``, ``DUMMY_NOTARY``, have moved to ``net.corda.testing``
  in the ``test-utils`` module.

* ``DummyContract``, ``DummyContractV2``, ``DummyLinearContract`` and ``DummyState`` have moved to ``net.corda.testing.contracts``
  in the ``test-utils`` modules.

* In Java, ``QueryCriteriaUtilsKt`` has moved to ``QueryCriteriaUtils``. Also ``and`` and ``or`` are now instance methods
  of ``QueryCriteria``.

* ``random63BitValue()`` has moved to ``CryptoUtils``

* Added additional common Sort attributes (see ``Sort.CommandStateAttribute``) for use in Vault Query criteria
  to include STATE_REF, STATE_REF_TXN_ID, STATE_REF_INDEX

* Moved the core flows previously found in ``net.corda.flows`` into ``net.corda.core.flows``. This is so that all packages
  in the ``core`` module begin with ``net.corda.core``.

* ``FinalityFlow`` can now be subclassed, and the ``broadcastTransaction`` and ``lookupParties`` function can be
  overridden in order to handle cases where no single transaction participant is aware of all parties, and therefore
  the transaction must be relayed between participants rather than sent from a single node.

* ``TransactionForContract`` has been removed and all usages of this class have been replaced with usage of
  ``LedgerTransaction``. In particular ``Contract.verify`` and the ``Clauses`` API have been changed and now take a
  ``LedgerTransaction`` as passed in parameter. The principal consequence of this is that the types of the input and output
  collections on the transaction object have changed, so it may be necessary to ``map`` down to the ``ContractState``
  sub-properties in existing code.

* Added various query methods to ``LedgerTransaction`` to simplify querying of states and commands. In the same vain
  ``Command`` is now parameterised on the ``CommandData`` field.

* Kotlin utilities that we deemed useful enough to keep public have been moved out of ``net.corda.core.Utils`` and into
  ``net.corda.core.utilities.KotlinUtils``. The other utilities have been marked as internal.

* Changes to ``Cordformation``/ cordapp building:

   * ``Cordformation`` modifies the JAR task to make cordapps build as semi fat JARs containing all dependencies
     except other cordapps and Corda core dependencies.
   * ``Cordformation`` adds a ``corda`` and ``cordaRuntime`` configuration to projects which cordapp developers should
     use to exclude core Corda JARs from being built into Cordapp fat JARs.

* ``database`` field in ``AbstractNode`` class has changed the type from ``org.jetbrains.exposed.sql.Database`` to
  ‘net.corda.node.utilities.CordaPersistence’ - no change is needed for the typical use
  (i.e. services.database.transaction {  code block } ) however a change is required when Database was explicitly declared

* ``DigitalSignature.LegallyIdentifiable``, previously used to identify a signer (e.g. in Oracles), has been removed.
  One can use the public key to derive the corresponding identity.

* Vault Query improvements and fixes:

    * FIX inconsistent behaviour: Vault Query defaults to UNCONSUMED in all QueryCriteria types

    * FIX serialization error: Vault Query over RPC when using custom attributes using VaultCustomQueryCriteria.

    * Aggregate function support: extended VaultCustomQueryCriteria and associated DSL to enable specification of
      aggregate functions (sum, max, min, avg, count) with, optional, group by clauses and sorting (on calculated aggregate).

    * Pagination simplification. Pagination continues to be optional, with following changes:

      - If no PageSpecification provided then a maximum of MAX_PAGE_SIZE (200) results will be returned, otherwise we fail-fast with a ``VaultQueryException`` to alert the API user to the need to specify a PageSpecification.
        Internally, we no longer need to calculate a results count (thus eliminating an expensive SQL query) unless a PageSpecification is supplied (note: that a value of -1 is returned for total_results in this scenario).
        Internally, we now use the AggregateFunction capability to perform the count.
      - Paging now starts from 1 (was previously 0).

    * Additional Sort criteria: by StateRef (or constituents: txId, index)

* Confidential identities API improvements

    * Registering anonymous identities now takes in AnonymousPartyAndPath
    * AnonymousParty.toString() now uses toStringShort() to match other toString() functions
    * Add verifyAnonymousIdentity() function to verify without storing an identity
    * Replace pathForAnonymous() with anonymousFromKey() which matches actual use-cases better
    * Add unit test for fetching the anonymous identity from a key
    * Update verifyAnonymousIdentity() function signature to match registerAnonymousIdentity()
    * Rename AnonymisedIdentity to AnonymousPartyAndPath
    * Remove certificate from AnonymousPartyAndPath as it's not actually used.
    * Rename registerAnonymousIdentity() to verifyAndRegisterAnonymousIdentity()

* Added JPA ``AbstractPartyConverter`` to ensure identity schema attributes are persisted securely according to type
  (well known party, resolvable anonymous party, completely anonymous party).

.. _changelog_m13:

Milestone 13
------------

Special thank you to `Frederic Dalibard <https://github.com/FredericDalibard>`_, for his contribution which adds
support for more currencies to the DemoBench and Explorer tools.

* A new Vault Query service:

   * Implemented using JPA and Hibernate, this new service provides the ability to specify advanced queries using
     criteria specification sets for both vault attributes and custom contract specific attributes. In addition, new
     queries provide sorting and pagination capabilities.
     The new API provides two function variants which are exposed for usage within Flows and by RPC clients:

     - ``queryBy()`` for point-in-time snapshot queries
       (replaces several existing VaultService functions and a number of Kotlin-only extension functions)
     - ``trackBy()`` for snapshot and streaming updates
       (replaces the VaultService ``track()`` function and the RPC ``vaultAndUpdates()`` function)

     Existing VaultService API methods will be maintained as deprecated until the following milestone release.

   * The NodeSchema service has been enhanced to automatically generate mapped objects for any ContractState objects
     that extend FungibleAsset or LinearState, such that common attributes of those parent states are persisted to
     two new vault tables: vault_fungible_states and vault_linear_states (and thus queryable using the new Vault Query
     service API).
     Similarly, two new common JPA superclass schemas (``CommonSchemaV1.FungibleState`` and
     ``CommonSchemaV1.LinearState``) mirror the associated FungibleAsset and LinearState interface states to enable
     CorDapp developers to create new custom schemas by extension (rather than duplication of common attribute mappings)

   * A new configurable field ``requiredSchemas`` has been added to the CordaPluginRegistry to enable CorDapps to
     register custom contract state schemas they wish to query using the new Vault Query service API (using the
     ``VaultCustomQueryCriteria``).

   * See :doc:`api-vault-query` for full details and code samples of using the new Vault Query service.

* Identity and cryptography related changes:

   * Enable certificate validation in most scenarios (will be enforced in all cases in an upcoming milestone).

   * Added DER encoded format for CompositeKey so they can be used in X.509 certificates.

   * Corrected several tests which made assumptions about counterparty keys, which are invalid when confidential
     identities are used.

   * A new RPC has been added to support fuzzy matching of X.500 names, for instance, to translate from user input to
     an unambiguous identity by searching the network map.

   * A function for deterministic key derivation ``Crypto.deriveKeyPair(privateKey: PrivateKey, seed: ByteArray)``
     has been implemented to support deterministic ``KeyPair`` derivation using an existing private key and a seed
     as inputs. This operation is based on the HKDF scheme and it's a variant of the hardened parent-private ->
     child-private key derivation function of the BIP32 protocol, but it doesn't utilize extension chain codes.
     Currently, this function supports the following schemes: ECDSA secp256r1 (NIST P-256), ECDSA secp256k1 and
     EdDSA ed25519.

* A new ``ClassWhitelist`` implementation, ``AllButBlacklisted`` is used internally to blacklist classes/interfaces,
  which are not expected to be serialised during checkpoints, such as ``Thread``, ``Connection`` and ``HashSet``.
  This implementation supports inheritance and if a superclass or superinterface of a class is blacklisted, so is
  the class itself. An ``IllegalStateException`` informs the user if a class is blacklisted and such an exception is
  returned before checking for ``@CordaSerializable``; thus, blacklisting precedes annotation checking.

* ``TimeWindow`` has a new 5th factory method ``TimeWindow.fromStartAndDuration(fromTime: Instant, duration: Duration)``
  which takes a start-time and a period-of-validity (after this start-time) as inputs.

* The node driver has moved to net.corda.testing.driver in the test-utils module.

* Web API related collections ``CordaPluginRegistry.webApis`` and ``CordaPluginRegistry.staticServeDirs`` moved to
  ``net.corda.webserver.services.WebServerPluginRegistry`` in ``webserver`` module.
  Classes serving Web API should now extend ``WebServerPluginRegistry`` instead of ``CordaPluginRegistry``
  and they should be registered in ``resources/META-INF/services/net.corda.webserver.services.WebServerPluginRegistry``.

* Added a flag to the driver that allows the running of started nodes in-process, allowing easier debugging.
  To enable use `driver(startNodesInProcess = true) { .. }`, or `startNode(startInSameProcess = true, ..)`
  to specify for individual nodes.

* Dependencies changes:
    * Upgraded Dokka to v0.9.14.
    * Upgraded Gradle Plugins to 0.12.4.
    * Upgraded Apache ActiveMQ Artemis to v2.1.0.
    * Upgraded Netty to v4.1.9.Final.
    * Upgraded BouncyCastle to v1.57.
    * Upgraded Requery to v1.3.1.

.. _changelog_m12:

Milestone 12 (First Public Beta)
--------------------------------

* Quite a few changes have been made to the flow API which should make things simpler when writing CorDapps:

   * ``CordaPluginRegistry.requiredFlows`` is no longer needed. Instead annotate any flows you wish to start via RPC with
     ``@StartableByRPC`` and any scheduled flows with ``@SchedulableFlow``.

   * ``CordaPluginRegistry.servicePlugins`` is also no longer used, along with ``PluginServiceHub.registerFlowInitiator``.
     Instead annotate your initiated flows with ``@InitiatedBy``. This annotation takes a single parameter which is the
     initiating flow. This initiating flow further has to be annotated with ``@InitiatingFlow``. For any services you
     may have, such as oracles, annotate them with ``@CordaService``. These annotations will be picked up automatically
     when the node starts up.

   * Due to these changes, when unit testing flows make sure to use ``AbstractNode.registerInitiatedFlow`` so that the flows
     are wired up. Likewise for services use ``AbstractNode.installCordaService``.

   * Related to ``InitiatingFlow``, the ``shareParentSessions`` boolean parameter of ``FlowLogic.subFlow`` has been
     removed. This was an unfortunate parameter that unnecessarily exposed the inner workings of flow sessions. Now, if
     your sub-flow can be started outside the context of the parent flow then annotate it with ``@InitiatingFlow``. If
     it's meant to be used as a continuation of the existing parent flow, such as ``CollectSignaturesFlow``, then it
     doesn't need any annotation.

   * The ``InitiatingFlow`` annotation also has an integer ``version`` property which assigns the initiating flow a version
     number, defaulting to 1 if it's not specified. This enables versioning of flows with nodes only accepting communication
     if the version number matches. At some point we will support the ability for a node to have multiple versions of the
     same flow registered, enabling backwards compatibility of flows.

   * ``ContractUpgradeFlow.Instigator`` has been renamed to just ``ContractUpgradeFlow``.

   * ``NotaryChangeFlow.Instigator`` has been renamed to just ``NotaryChangeFlow``.

   * ``FlowLogic.getCounterpartyMarker`` is no longer used and been deprecated for removal. If you were using this to
     manage multiple independent message streams with the same party in the same flow then use sub-flows instead.

* There are major changes to the ``Party`` class as part of confidential identities:

    * ``Party`` has moved to the ``net.corda.core.identity`` package; there is a deprecated class in its place for
      backwards compatibility, but it will be removed in a future release and developers should move to the new class as soon
      as possible.
    * There is a new ``AbstractParty`` superclass to ``Party``, which contains just the public key. This now replaces
      use of ``Party`` and ``PublicKey`` in state objects, and allows use of full or anonymised parties depending on
      use-case.
    * A new ``PartyAndCertificate`` class has been added which aggregates a Party along with an X.509 certificate and
      certificate path back to a network trust root. This is used where a Party and its proof of identity are required,
      for example in identity registration.
    * Names of parties are now stored as a ``X500Name`` rather than a ``String``, to correctly enforce basic structure of the
      name. As a result all node legal names must now be structured as X.500 distinguished names.

* The identity management service takes an optional network trust root which it will validate certificate paths to, if
  provided. A later release will make this a required parameter.

* There are major changes to transaction signing in flows:

     * You should use the new ``CollectSignaturesFlow`` and corresponding ``SignTransactionFlow`` which handle most
       of the details of this for you. They may get more complex in future as signing becomes a more featureful
       operation. ``ServiceHub.legalIdentityKey`` no longer returns a ``KeyPair``, it instead returns just the
       ``PublicKey`` portion of this pair. The ``ServiceHub.notaryIdentityKey`` has changed similarly. The goal of this
       change is to keep private keys encapsulated and away from most flow code/Java code, so that the private key
       material can be stored in HSMs and other key management devices.
     * The ``KeyManagementService`` no longer provides any mechanism to request the node's ``PrivateKey`` objects directly.
       Instead signature creation occurs in the ``KeyManagementService.sign``, with the ``PublicKey`` used to indicate
       which of the node's keypairs to use. This lookup also works for ``CompositeKey`` scenarios
       and the service will search for a leaf key hosted on the node.
     * The ``KeyManagementService.freshKey`` method now returns only the ``PublicKey`` portion of the newly generated ``KeyPair``
       with the ``PrivateKey`` kept internally to the service.
     * Flows which used to acquire a node's ``KeyPair``, typically via ``ServiceHub.legalIdentityKey``,
       should instead use the helper methods on ``ServiceHub``. In particular to freeze a ``TransactionBuilder`` and
       generate an initial partially signed ``SignedTransaction`` the flow should use ``ServiceHub.toSignedTransaction``.
       Flows generating additional party signatures should use ``ServiceHub.createSignature``. Each of these methods is
       provided with two signatures. One version that signs with the default node key, the other which allows key selection
       by passing in the ``PublicKey`` partner of the desired signing key.
     * The original ``KeyPair`` signing methods have been left on the ``TransactionBuilder`` and ``SignedTransaction``, but
       should only be used as part of unit testing.

* ``Timestamp`` used for validation/notarization time-range has been renamed to ``TimeWindow``.
   There are now 4 factory methods ``TimeWindow.fromOnly(fromTime: Instant)``,
   ``TimeWindow.untilOnly(untilTime: Instant)``, ``TimeWindow.between(fromTime: Instant, untilTime: Instant)`` and
   ``TimeWindow.withTolerance(time: Instant, tolerance: Duration)``.
   Previous constructors ``TimeWindow(fromTime: Instant, untilTime: Instant)`` and
   ``TimeWindow(time: Instant, tolerance: Duration)`` have been removed.

* The Bouncy Castle library ``X509CertificateHolder`` class is now used in place of ``X509Certificate`` in order to
  have a consistent class used internally. Conversions to/from ``X509Certificate`` are done as required, but should
  be avoided where possible.

* The certificate hierarchy has been changed in order to allow corda node to sign keys with proper certificate chain.
     * The corda node will now be issued a restricted client CA for identity/transaction key signing.
     * TLS certificate are now stored in `sslkeystore.jks` and identity keys are stored in `nodekeystore.jks`

.. warning:: The old keystore will need to be removed when upgrading to this version.

Milestone 11.1
--------------

* Fix serialisation error when starting a flow.
* Automatically whitelist subclasses of `InputStream` when serialising.
* Fix exception in DemoBench on Windows when loading CorDapps into the Node Explorer.
* Detect when localhost resolution is broken on MacOSX, and provide instructions on how to fix it.

Milestone 11.0
--------------

* API changes:
    * Added extension function ``Database.transaction`` to replace ``databaseTransaction``, which is now deprecated.

    * Starting a flow no longer enables progress tracking by default. To enable it, you must now invoke your flow using
      one of the new ``CordaRPCOps.startTrackedFlow`` functions. ``FlowHandle`` is now an interface, and its ``progress: Observable``
      field has been moved to the ``FlowProgressHandle`` child interface. Hence developers no longer need to invoke ``notUsed``
      on their flows' unwanted progress-tracking observables.

    * Moved ``generateSpend`` and ``generateExit`` functions into ``OnLedgerAsset`` from the vault and
      ``AbstractConserveAmount`` clauses respectively.

    * Added ``CompositeSignature`` and ``CompositeSignatureData`` as part of enabling ``java.security`` classes to work
      with composite keys and signatures.

    * ``CompositeKey`` now implements ``java.security.PublicKey`` interface, so that keys can be used on standard classes
      such as ``Certificate``.

        * There is no longer a need to transform single keys into composite - ``composite`` extension was removed, it is
          impossible to create ``CompositeKey`` with only one leaf.

        * Constructor of ``CompositeKey`` class is now private. Use ``CompositeKey.Builder`` to create a composite key.
          Keys emitted by the builder are normalised so that it's impossible to create a composite key with only one node.
          (Long chains of single nodes are shortened.)

        * Use extension function ``PublicKeys.keys`` to access all keys belonging to an instance of ``PublicKey``. For a
          ``CompositeKey``, this is equivalent to ``CompositeKey.leafKeys``.

        * Introduced ``containsAny``, ``isFulfilledBy``, ``keys`` extension functions on ``PublicKey`` - ``CompositeKey``
          type checking is done there.

* Corda now requires JDK 8u131 or above in order to run. Our Kotlin now also compiles to JDK8 bytecode, and so you'll need
  to update your CorDapp projects to do the same. E.g. by adding this to ``build.gradle``:

.. parsed-literal::

    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).all {
        kotlinOptions {
            languageVersion = "1.1"
            apiVersion = "1.1"
            jvmTarget = "1.8"
        }
    }

..

 or by adjusting ``Settings/Build,Execution,Deployment/Compiler/KotlinCompiler`` in IntelliJ::

 -  Language Version: 1.1
 -  API Version: 1.1
 -  Target JVM Version: 1.8

* DemoBench is now installed as ``Corda DemoBench`` instead of ``DemoBench``.

* Rewrote standard test identities to have full X.500 distinguished names. As part of this work we standardised on a
  smaller set of test identities, to reduce risk of subtle differences (i.e. similar common names varying by whitespace)
  in naming making it hard to diagnose issues.

Milestone 10.0
--------------

Special thank you to `Qian Hong <https://github.com/fracting>`_, `Marek Skocovsky <https://github.com/marekdapps>`_,
`Karel Hajek <https://github.com/polybioz>`_, and `Jonny Chiu <https://github.com/johnnyychiu>`_ for their contributions
to Corda in M10.

.. warning:: Due to incompatibility between older version of IntelliJ and gradle 3.4, you will need to upgrade Intellij
   to 2017.1 (with kotlin-plugin v1.1.1) in order to run Corda demos in IntelliJ. You can download the latest IntelliJ
   from `JetBrains <https://www.jetbrains.com/idea/download/>`_.

.. warning:: The Kapt-generated models are no longer included in our codebase. If you experience ``unresolved references``
   errors when building in IntelliJ, please rebuild the schema model by running ``gradlew kaptKotlin`` in Windows or
   ``./gradlew kaptKotlin`` in other systems. Alternatively, perform a full gradle build or install.

.. note:: Kapt is used to generate schema model and entity code (from annotations in the codebase) using the Kotlin Annotation
   processor.

* Corda DemoBench:
    * DemoBench is a new tool to make it easy to configure and launch local Corda nodes. A very useful tool to demonstrate
      to your colleagues the fundamentals of Corda in real-time. It has the following features:

        * Clicking "Add node" creates a new tab that lets you edit the most important configuration properties of the node
          before launch, such as its legal name and which CorDapps will be loaded.
        * Each tab contains a terminal emulator, attached to the pseudoterminal of the node. This lets you see console output.
        * You can launch an Corda Explorer instance for each node via the DemoBench UI. Credentials are handed to the Corda
          Explorer so it starts out logged in already.
        * Some basic statistics are shown about each node, informed via the RPC connection.
        * Another button launches a database viewer in the system browser.
        * The configurations of all running nodes can be saved into a single ``.profile`` file that can be reloaded later.

    * Download `Corda DemoBench <https://www.corda.net/downloads/>`_.

* Vault:
    * Soft Locking is a new feature implemented in the vault which prevent a node constructing transactions that attempt
      to use the same input(s) simultaneously.
    * Such transactions would result in naturally wasted effort when the notary rejects them as double spend attempts.
    * Soft locks are automatically applied to coin selection (eg. cash spending) to ensure that no two transactions attempt
      to spend the same fungible states.

* Corda Shell :
    * The shell lets developers and node administrators easily command the node by running flows, RPCs and SQL queries.
    * It provides a variety of commands to monitor the node.
    * The Corda Shell is based on the popular `CRaSH project <http://www.crashub.org/>`_ and new commands can be easily
      added to the node by simply dropping Groovy or Java files into the node's ``shell-commands`` directory.
    * We have many enhancements planned over time including SSH access, more commands and better tab completion.

* API changes:
    * The new Jackson module provides JSON/YAML serialisers for common Corda datatypes.
      If you have previously been using the JSON support in the standalone web server,
      please be aware that Amounts are now serialised as strings instead of { quantity, token } pairs as before.
      The old format is still accepted, but the new JSON will be produced using strings like "1000.00 USD" when writing.
      You can use any format supported by ``Amount.parseCurrency`` as input.

    * We have restructured client package in this milestone.
        * ``CordaClientRPC`` is now in the new ``:client:rpc`` module.
        * The old ``:client`` module has been split up into ``:client:jfx`` and ``:client:mock``.
        * We also have a new ``:node-api`` module (package ``net.corda.nodeapi``) which contains the shared code between
          ``node`` and ``client``.

    * The basic Amount API has been upgraded to have support for advanced financial use cases and to better integrate with
      currency reference data.

* Configuration:
    * Replace ``artemisPort`` with ``p2pPort`` in Gradle configuration.
    * Replace ``artemisAddress`` with ``p2pAddress`` in node configuration.
    * Added ``rpcAddress`` in node configuration for non-ssl RPC connection.

* Object Serialization:
    * Pool Kryo instances for efficiency.

* RPC client changes:
    * RPC clients can now connect to the node without the need for SSL. This requires a separate port on the Artemis broker,
      SSL must not be used for RPC connection.
    * CordaRPCClient now needs to connect to ``rpcAddress`` rather than ``p2pAddress``.

* Dependencies changes:
    * Upgraded Kotlin to v1.1.1.
    * Upgraded Gradle to v3.4.1.
    * Upgraded requery to v1.2.1.
    * Upgraded H2 to v1.4.194.
    * Replaced kotlinx-support-jdk8 with kotlin-stdlib-jre8.

* Improvements:
    * Added ``--version`` command line flag to print the version of the node.
    * Flows written in Java can now execute a sub-flow inside ``UntrustworthyData.unwrap``.
    * Added optional out-of-process transaction verification. Any number of external verifier processes may be attached
      to the node which can handle loadbalanced verification requests.

* Bug fixes:
    * ``--logging-level`` command line flag was previously broken, now correctly sets the logging level.
    * Fixed bug whereby Cash Exit was not taking into account the issuer reference.


Milestone 9.1
-------------

* Correct web server ports for IRS demo.
* Correct which corda-webserver JAR is published to Maven.

Milestone 9
-----------

* With thanks to `Thomas Schroeter <https://github.com/thschroeter>`_ for the Byzantine fault tolerant (BFT)
  notary prototype.
* Web server is a separate JAR.  This is a breaking change. The new webserver JAR (``corda-webserver.jar``)
  must be invoked separately to node startup, using the command``java -jar corda-webserver.jar`` in the same
  directory as the ``node.conf``. Further changes are anticipated in upcoming milestone releases.

* API:

    * Pseudonymous ``AnonymousParty`` class added as a superclass of ``Party``.
    * Split ``CashFlow`` into individual ``CashIssueFlow``, ``CashPaymentFlow`` and ``CashExitFlow`` flows, so that fine
      grained permissions can be applied. Added ``CashFlowCommand`` for use-cases where cash flow triggers need to be
      captured in an object that can be passed around.
    * ``CordaPluginRegistry`` method ``registerRPCKryoTypes`` is renamed ``customizeSerialization`` and the argument
      types now hide the presence of Kryo.
    * New extension functions for encoding/decoding to base58, base64, etc. See
      ``core/src/main/kotlin/net/corda/core/crypto/EncodingUtils.kt``
    * Add ``openAttachment`` function to Corda RPC operations, for downloading an attachment from a node's data storage.
    * Add ``getCashBalances`` function to Corda RPC operations, for getting cash balances from a node's vault.

* Configuration:
    * ``extraAdvertisedServiceIds`` config is now a list of strings, rather than a comma separated string. For example
      ``[ "corda.interest_rates" ]`` instead of ``"corda.interest_rates"``.

* Flows:
    * Split ``CashFlow`` into separate ``CashIssueFlow``, ``CashPaymentFlow`` and ``CashExitFlow`` so that permissions can
      be assigned individually.
    * Split single example user into separate "bankUser" and "bigCorpUser" so that permissions for the users make sense
      rather than being a combination of both roles.
    * ``ProgressTracker`` emits exception thrown by the flow, allowing the ANSI renderer to correctly stop and print the error

* Object Serialization:

    * Consolidated Kryo implementations across RPC and P2P messaging with whitelisting of classes via plugins or with
      ``@CordaSerializable`` for added node security.

* Privacy:
    * Non-validating notary service now takes in a ``FilteredTransaction`` so that no potentially sensitive transaction
      details are unnecessarily revealed to the notary

* General:
    * Add vault service persistence using Requery
    * Certificate signing utility output is now more verbose

Milestone 8
-----------

* Node memory usage and performance improvements, demo nodes now only require 200 MB heap space to run.

* The Corda node no longer runs an internal web server, it's now run in a separate process. Driver and Cordformation have
  been updated to reflect this change. Existing CorDapps should be updated with additional calls to the new ``startWebserver()``
  interface in their Driver logic (if they use the driver e.g. in integration tests). See the IRS demo for an example.

* Data model: ``Party`` equality is now based on the owning key, rather than the owning key and name. This is important for
  party anonymisation to work, as each key must identify exactly one party.

* Contracts: created new composite clauses called ``AllOf``, ``AnyOf`` and ``FirstOf`` to replace ``AllComposition``, ``AnyComposition``
  and ``FirstComposition``, as this is significantly clearer in intent. ``AnyOf`` also enforces that at least one subclause
  must match, whereas ``AnyComposition`` would accept no matches.

* Explorer: the user can now configure certificate path and keystore/truststore password on the login screen.

* Documentation:

    * Key Concepts section revamped with new structure and content.
    * Added more details to :doc:`getting-set-up` page.

* Flow framework: improved exception handling with the introduction of ``FlowException``. If this or a subtype is thrown
  inside a flow it will propagate to all counterparty flows and subsequently be thrown by them as well. Existing flows such as
  ``NotaryFlow.Client/Service`` and others have been modified to throw a ``FlowException`` (in this particular case a
  ``NotaryException``) instead of sending back error responses.

* Notary flow: provide complete details of underlying error when contract validation fails.

Milestone 7
-----------

* With thanks to `Thomas Schroeter <https://github.com/thschroeter>`_ ``NotaryFlow`` is now idempotent.

* Explorer:

    * The GUI for the explorer now shows other nodes on the network map and the transactions between them.
    * Map resolution increased and allows zooming and panning.
    * `Video demonstration <https://www.corda.net/2017/01/03/the-node-explorer/>`_ of the Node Explorer.

* The CorDapp template now has a Java example that parallels the Kotlin one for developers more comfortable with Java.
  ORM support added to the Kotlin example.

* Demos:

    * Added the Bank of Corda demo - a demo showing a node (Bank of Corda) acting as an issuer of Cash, and a client
      driver providing both Web and RPC access to request issuance of cash.
    * Demos now use RPC to communicate with the node from the webserver. This brings the demos more in line with how
      interaction with nodes is expected to be. The demos now treat their webservers like clients. This will also allow
      for the splitting of the webserver from the node for milestone 8.
    * Added a SIMM valuation demo integration test to catch regressions.

* Security:

    * MQ broker of the node now requires authentication which means that third parties cannot connect to and
      listen to queues on the Node. RPC and P2P between nodes is now authenticated as a result of this change.
      This also means that nodes or RPC users cannot pretend to be other nodes or RPC users.
    * The node now does host verification of any node that connects to it and prevents man in the middle attacks.

* Improvements:

    * Vault updates now contain full ``StateAndRef`` which allows subscribers to check whether the update contains
      relevant states.
    * Cash balances are calculated using aggregate values to prevent iterating through all states in the vault, which
      improves performance.
    * Multi-party services, such as notaries, are now load balanced and represented as a single ``Party`` object.
    * The Notary Change flow now supports encumbrances.

Milestone 6
-----------

* Added the `Corda technical white paper <_static/corda-technical-whitepaper.pdf>`_. Note that its current version
  is 0.5 to reflect the fact that the Corda design is still evolving. Although we expect only relatively small tweaks
  at this point, when Corda reaches 1.0 so will the white paper.

* Major documentation restructuring and new content:

    * More details on Corda node internals.
    * New CorDapp tutorial.
    * New tutorial on building transactions.
    * New tutorials on how to run and use a notary service.

* An experimental version of the deterministic JVM sandbox has been added. It is not integrated with the node and will
  undergo some significant changes in the coming releases before it is integrated, as the code is finished, as bugs are
  found and fixed, and as the platform subset we choose to expose is finalised. Treat this as an outline of the basic
  approach rather than something usable for production.

* Developer experience:

    * Samples have been merged back into the main repository. All samples can now be run via command line or IntelliJ.

    * Added a Client RPC python example.

    * Node console output now displays concise startup information, such as startup time or web address. All logging to
      the console is suppressed apart from errors and flow progress tracker steps. It can be re-enabled by passing
      ``--log-to-console`` command line parameter. Note that the log file remains unchanged and will still contain all
      log entries.

    * The ``runnodes`` scripts generated by the Gradle plugins now open each node in separate terminal windows or (on macOS) tabs.

    * A much more complete template app.

    * JARs now available on Maven Central.

* Data model: A party is now identified by a composite key (formerly known as a "public key tree") instead of a single public key.
  Read more in :ref:`composite-keys`. This allows expressing distributed service identities, e.g. a distributed notary.
  In the future this will also allow parties to use multiple signing keys for their legal identity.

* Decentralised consensus: A prototype RAFT based notary composed of multiple nodes has been added. This implementation
  is optimised for high performance over robustness against malicious cluster members, which may be appropriate for
  some financial situations.

* Node explorer app:

    * New theme aligned with the Corda branding.
    * The New Transaction screen moved to the Cash View (as it is used solely for cash transactions)
    * Removed state machine/flow information from Transaction table. A new view for this will be created in a future release.
    * Added a new Network View that displays details of all nodes on the network.
    * Users can now configure the reporting currency in settings.
    * Various layout and performance enhancements.

* Client RPC:

    * Added a generic ``startFlow`` method that enables starting of any flow, given sufficient permissions.
    * Added the ability for plugins to register additional classes or custom serialisers with Kryo for use in RPC.
    * ``rpc-users.properties`` file has been removed with RPC user settings moved to the config file.

* Configuration changes: It is now possible to specify a custom legal name for any of the node's advertised services.

* Added a load testing framework which allows stress testing of a node cluster, as well as specifying different ways of
  disrupting the normal operation of nodes. See :doc:`loadtesting`.

* Improvements to the experimental contract DSL, by Sofus Mortensen of Nordea Bank (please give Nordea a shoutout too).

API changes:

* The top level package has been renamed from ``com.r3corda`` to ``net.corda``.
* Protocols have been renamed to "flows".
* ``OpaqueBytes`` now uses ``bytes`` as the field name rather than ``bits``.

Milestone 5
-----------

* A simple RPC access control mechanism. Users, passwords and permissions can be defined in a configuration file.
  This mechanism will be extended in future to support standard authentication systems like LDAP.

* New features in the explorer app and RPC API for working with cash:

    * Cash can now be sent, issued and exited via RPC.
    * Notes can now be associated with transactions.
    * Hashes are visually represented using identicons.
    * Lots of functional work on the explorer UI. You can try it out by running ``gradle tools:explorer:runDemoNodes`` to run
      a local network of nodes that swap cash with each other, and then run ``gradle tools:explorer:run`` to start
      the app.

* A new demo showing shared valuation of derivatives portfolios using the ISDA SIMM has been added. Note that this app
  relies on a proprietary implementation of the ISDA SIMM business logic from OpenGamma. A stub library is provided
  to ensure it compiles but if you want to use the app for real please contact us.

* Developer experience (we plan to do lots more here in milestone 6):

    * Demos and samples have been split out of the main repository, and the initial developer experience continues to be
      refined. All necessary JARs can now be installed to Maven Local by simply running ``gradle install``.
    * It's now easier to define a set of nodes to run locally using the new "CordFormation" gradle plugin, which
      defines a simple DSL for creating networks of nodes.
    * The template CorDapp has been upgraded with more documentation and showing more features.

* Privacy: transactions are now structured as Merkle trees, and can have sections "torn off" - presented for
  verification and signing without revealing the rest of the transaction.

* Lots of bug fixes, tweaks and polish starting the run up to the open source release.

API changes:

* Plugin service classes now take a ``PluginServiceHub`` rather than a ``ServiceHubInternal``.
* ``UniqueIdentifier`` equality has changed to only take into account the underlying UUID.
* The contracts module has been renamed to finance, to better reflect what it is for.

Milestone 4
-----------

New features in this release:

* Persistence:

    * States can now be written into a relational database and queried using JDBC. The schemas are defined by the
      smart contracts and schema versioning is supported. It is reasonable to write an app that stores data in a mix
      of global ledger transactions and local database tables which are joined on demand, using join key slots that
      are present in many state definitions. Read more about :doc:`api-persistence`.
    * The embedded H2 SQL database is now exposed by default to any tool that can speak JDBC. The database URL is
      printed during node startup and can be used to explore the database, which contains both node internal data
      and tables generated from ledger states.
    * Protocol checkpoints are now stored in the database as well. Message processing is now atomic with protocol
      checkpointing and run under the same RDBMS transaction.
    * MQ message deduplication is now handled at the app layer and performed under the RDMS transaction, so
      ensuring messages are only replayed if the RDMS transaction rolled back.
    * "The wallet" has been renamed to "the vault".

* Client RPC:

    * New RPCs added to subscribe to snapshots and update streams state of the vault, currently executing protocols
      and other important node information.
    * New tutorial added that shows how to use the RPC API to draw live transaction graphs on screen.

* Protocol framework:

    * Large simplifications to the API. Session management is now handled automatically. Messages are now routed
      based on identities rather than node IP addresses.

* Decentralised consensus:

    * A standalone one-node notary backed by a JDBC store has been added.
    * A prototype RAFT based notary composed of multiple nodes is available on a branch.

* Data model:

    * Compound keys have been added as preparation for merging a distributed RAFT based notary. Compound keys
      are trees of public keys in which interior nodes can have validity thresholds attached, thus allowing
      boolean formulas of keys to be created. This is similar to Bitcoin's multi-sig support and the data model
      is the same as the InterLedger Crypto-Conditions spec, which should aid interoperate in future. Read more about
      key trees in the ":doc:`api-core-types`" article.
    * A new tutorial has been added showing how to use transaction attachments in more detail.

* Testnet

    * Permissioning infrastructure phase one is built out. The node now has a notion of developer mode vs normal
      mode. In developer mode it works like M3 and the SSL certificates used by nodes running on your local
      machine all self-sign using a developer key included in the source tree. When developer mode is not active,
      the node won't start until it has a signed certificate. Such a certificate can be obtained by simply running
      an included command line utility which generates a CSR and submits it to a permissioning service, then waits
      for the signed certificate to be returned. Note that currently there is no public Corda testnet, so we are
      not currently running a permissioning service.

* Standalone app development:

    * The Corda libraries that app developers need to link against can now be installed into your local Maven
      repository, where they can then be used like any other JAR. See :doc:`running-a-node`.

* User interfaces:

    * Infrastructure work on the node explorer is now complete: it is fully switched to using the MQ based RPC system.
    * A library of additional reactive collections has been added. This API builds on top of Rx and the observable
      collections API in Java 8 to give "live" data structures in which the state of the node and ledger can be
      viewed as an ordinary Java ``List``, ``Map`` and ``Set``, but which also emit callbacks when these views
      change, and which can have additional views derived in a functional manner (filtered, mapped, sorted, etc).
      Finally, these views can then be bound directly into JavaFX UIs. This makes for a concise and functional
      way of building application UIs that render data from the node, and the API is available for third party
      app developers to use as well. We believe this will be highly productive and enjoyable for developers who
      have the option of building JavaFX apps (vs web apps).
    * The visual network simulator tool that was demoed back in April as part of the first Corda live demo has
      been merged into the main repository.

* Documentation

    * New secure coding guidelines. Corda tries to eliminate as many security mistakes as practical via the type
      system and other mechanically checkable processes, but there are still things that one must be aware of.
    * New attachments tutorial.
    * New Client RPC tutorial.
    * More tutorials on how to build a standalone CorDapp.

* Testing

    * More integration testing support
    * New micro-DSLs for expressing expected sequences of operations with more or less relaxed ordering constraints.
    * QuickCheck generators to create streams of randomised transactions and other basic types. QuickCheck is a way
      of writing unit tests that perform randomised fuzz testing of code, originally developed by the Haskell
      community and now also available in Java.

API changes:

* The transaction types (Signed, Wire, LedgerTransaction) have moved to ``net.corda.core.transactions``. You can
  update your code by just deleting the broken import lines and letting your IDE re-import them from the right
  location.
* ``AbstractStateReplacementProtocol.verifyProposal`` has changed its prototype in a minor way.
* The ``UntrustworthyData<T>.validate`` method has been renamed to ``unwrap`` - the old name is now deprecated.
* The wallet, wallet service, etc. are now vault, vault service, etc. These better reflect the intent that they
  are a generic secure data store, rather than something which holds cash.
* The protocol send/receive APIs have changed to no longer require a session id. Please check the current version
  of the protocol framework tutorial for more details.

Milestone 3
-----------

* More work on preparing for the testnet:

    * Corda is now a standalone app server that loads "CorDapps" into itself as plugins. Whilst the existing IRS
      and trader demos still exist for now, these will soon be removed and there will only be a single Corda node
      program. Note that the node is a single, standalone jar file that is easier to execute than the demos.
    * Project Vega (shared SIMM modelling for derivative portfolios) has already been converted to be a CorDapp.
    * Significant work done on making the node persist its wallet data to a SQL backend, with more on the way.
    * Upgrades and refactorings of the core transaction types in preparation for the incoming sandboxing work.

* The Clauses API that seeks to make writing smart contracts easier has gone through another design iteration,
  with the result that clauses are now cleaner and more composable.
* Improvements to the protocol API for finalising transactions (notarising, transmitting and storing).
* Lots of work done on an MQ based client API.
* Improvements to the developer site:

    * The developer site has been re-read from start to finish and refreshed for M3 so there should be no obsolete
      texts or references anywhere.
    * The Corda non-technical white paper is now a part of the developer site and git repository. The LaTeX source is
      also provided so if you spot any issues with it, you can send us patches.
    * There is a new section on how to write CorDapps.

* Further R&D work by Sofus Mortensen in the experimental module on a new 'universal' contract language.
* SSL for the REST API and webapp server can now be configured.


Milestone 2
-----------

* Big improvements to the interest rate swap app:

    * A new web app demonstrating the IRS contract has been added. This can be used as an example for how to interact with
      the Corda API from the web.
    * Simplifications to the way the demo is used from the command line.
    * :doc:`Detailed documentation on how the contract works and can be used <contract-irs>` has been written.
    * Better integration testing of the app.

* Smart contracts have been redesigned around reusable components, referred to as "clauses". The cash, commercial paper
  and obligation contracts now share a common issue clause.
* New code in the experimental module (note that this module is a place for work-in-progress code which has not yet gone
  through code review and which may, in general, not even function correctly):

    * Thanks to the prolific Sofus Mortensen @ Nordea Bank, an experimental generic contract DSL that is based on the famous
      2001 "Composing contracts" paper has been added. We thank Sofus for this great and promising research, which is so
      relevant in the wake of the DAO hack.
    * The contract code from the recent trade finance demos is now in experimental. This code comes thanks to a
      collaboration of the members; all credit to:

        * Mustafa Ozturk @ Natixis
        * David Nee @ US Bank
        * Johannes Albertsen @ Dankse Bank
        * Rui Hu @ Nordea
        * Daniele Barreca @ Unicredit
        * Sukrit Handa @ Scotiabank
        * Giuseppe Cardone @ Banco Intesa
        * Robert Santiago @ BBVA

* The usability of the command line demo programs has been improved.
* All example code and existing contracts have been ported to use the new Java/Kotlin unit testing domain-specific
  languages (DSLs) which make it easy to construct chains of transactions and verify them together. This cleans up
  and unifies the previous ad-hoc set of similar DSLs. A tutorial on how to use it has been added to the documentation.
  We believe this largely completes our testing story for now around smart contracts. Feedback from bank developers
  during the Trade Finance project has indicated that the next thing to tackle is docs and usability improvements in
  the protocols API.
* Significant work done towards defining the "CorDapp" concept in code, with dynamic loading of API services and more to
  come.
* Inter-node communication now uses SSL/TLS and AMQP/1.0, albeit without all nodes self-signing at the moment. A real
  PKI for the p2p network will come later.
* Logging is now saved to files with log rotation provided by Log4J.

API changes:

* Some utility methods and extension functions that are specific to certain contract types have moved packages: just
  delete the import lines that no longer work and let IntelliJ replace them with the correct package paths.
* The ``arg`` method in the test DSL is now called ``command`` to be consistent with the rest of the data model.
* The messaging APIs have changed somewhat to now use a new ``TopicSession`` object. These APIs will continue to change
  in the upcoming releases.
* Clauses now have default values provided for ``ifMatched``, ``ifNotMatched`` and ``requiredCommands``.

New documentation:

* :doc:`contract-catalogue`
* :doc:`contract-irs`
* :doc:`tutorial-test-dsl`

Milestone 1
-----------

Highlights of this release:

* Event scheduling. States in the ledger can now request protocols to be invoked at particular times, for states
  considered relevant by the wallet.
* Upgrades to the notary/consensus service support:

    * There is now a way to change the notary controlling a state.
    * You can pick between validating and non-validating notaries, these let you select your privacy/robustness trade-off.

* A new obligation contract that supports bilateral and multilateral netting of obligations, default tracking and
  more.
* Improvements to the financial type system, with core classes and contracts made more generic.
* Switch to a better digital signature algorithm: ed25519 instead of the previous JDK default of secp256r1.
* A new integration test suite.
* A new Java unit testing DSL for contracts, similar in spirit to the one already developed for Kotlin users (which
  depended on Kotlin specific features).
* An experimental module, where developers who want to work with the latest Corda code can check in contracts/cordapp
  code before it's been fully reviewed. Code in this module has compiler warnings suppressed but we will still make
  sure it compiles across refactorings.
* Persistence improvements: transaction data is now stored to disk and automatic protocol resume is now implemented.
* Many smaller bug fixes, cleanups and improvements.

We have new documentation on:

* :doc:`event-scheduling`
* :doc:`api-core-types`
* :doc:`key-concepts-consensus`

Summary of API changes (not exhaustive):

* Notary/consensus service:

    * ``NotaryService`` is now extensible.
    * Every ``ContractState`` now has to specify a *participants* field, which is a list of parties that are able to
      consume this state in a valid transaction. This is used for e.g. making sure all relevant parties obtain the updated
      state when changing a notary.
    * Introduced ``TransactionState``, which wraps ``ContractState``, and is used when defining a transaction output.
      The notary field is moved from ``ContractState`` into ``TransactionState``.
    * Every transaction now has a *type* field, which specifies custom build & validation rules for that transaction type.
      Currently two types are supported: General (runs the default build and validation logic) and NotaryChange (
      contract code is not run during validation, checks that the notary field is the only difference between the
      inputs and outputs).
      ``TransactionBuilder()`` is now abstract, you should use ``TransactionType.General.Builder()`` for building transactions.

* The cash contract has moved from ``net.corda.contracts`` to ``net.corda.contracts.cash``
* ``Amount`` class is now generic, to support non-currency types such as physical assets. Where you previously had just
  ``Amount``, you should now use ``Amount<Currency>``.
* Refactored the Cash contract to have a new FungibleAsset superclass, to model all countable assets that can be merged
  and split (currency, barrels of oil, etc.)
* Messaging:

    * ``addMessageHandler`` now has a different signature as part of error handling changes.
    * If you want to return nothing to a protocol, use ``Ack`` instead of ``Unit`` from now on.

* In the IRS contract, dateOffset is now an integer instead of an enum.
* In contracts, you now use ``tx.getInputs`` and ``tx.getOutputs`` instead of ``getInStates`` and ``getOutStates``. This is
  just a renaming.
* A new ``NonEmptySet`` type has been added for cases where you wish to express that you have a collection of unique
  objects which cannot be empty.
* Please use the global ``newSecureRandom()`` function rather than instantiating your own SecureRandom's from now on, as
  the custom function forces the use of non-blocking random drivers on Linux.

Milestone 0
-----------

This is the first release, which includes:

* Some initial smart contracts: cash, commercial paper, interest rate swaps
* An interest rate oracle
* The first version of the protocol/orchestration framework
* Some initial support for pluggable consensus mechanisms
* Tutorials and documentation explaining how it works
* Much more ...
