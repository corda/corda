Changelog
=========

Here's a summary of what's changed in each Corda release. For guidance on how to upgrade code from the previous
release, see :doc:`upgrade-notes`.

Version 3.4
-----------

* Added a mode to the Class Carpenter where classes it cannot synthesise that are removed from an objects dependency
  graph by the evolution code no longer causes an error. This is a small enhancement to better facilitate compatibility
  with newer version of Corda. It allows Version 3 nodes to receive serialised forms of evolved types and cope with
  them in a more elegant way than previously.

* Serialization and class synthesis fixes.

* Documentation updates

* Information about checkpointed flows can be retrieved from the shell. Calling ``dumpCheckpoints`` will create a zip file inside the node's
  ``log`` directory. This zip will contain a JSON representation of each checkpointed flow. This information can then be used to determine the
  state of stuck flows or flows that experienced internal errors and were kept in the node for manual intervention.

Version 3.3
-----------

* Vault query fix: support query by parent classes of Contract State classes (see https://github.com/corda/corda/issues/3714)

* Fixed an issue preventing Shell from returning control to the user when CTRL+C is pressed in the terminal.

* Fixed a problem that sometimes prevented nodes from starting in presence of custom state types in the database without a corresponding type from installed CorDapps.

* Introduced a grace period before the initial node registration fails if the node cannot connect to the Doorman.
  It retries 10 times with a 1 minute interval in between each try. At the moment this is not configurable.

* Fixed an error thrown by NodeVaultService upon recording a transaction with a number of inputs greater than the default page size.

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
    The encoded bytes are also serialised into the ``encoded`` field. This can be used to deserialise an ``X509Certificate``
    back.
  * ``CertPath`` objects are serialised as a list of ``X509Certificate`` objects.

* ``fullParties`` boolean parameter added to ``JacksonSupport.createDefaultMapper`` and ``createNonRpcMapper``. If ``true``
  then ``Party`` objects are serialised as JSON objects with the ``name`` and ``owningKey`` fields. For ``PartyAndCertificate``
  the ``certPath`` is serialised.

* Several members of ``JacksonSupport`` have been deprecated to highlight that they are internal and not to be used

* ``ServiceHub`` and ``CordaRPCOps`` can now safely be used from multiple threads without incurring in database transaction problems.

* Fixed an issue preventing out of process nodes started by the ``Driver`` from logging to file.

* The Vault Criteria API has been extended to take a more precise specification of which class contains a field. This primarily impacts Java users; Kotlin users need take no action. The old methods have been deprecated but still work - the new methods avoid bugs that can occur when JPA schemas inherit from each other.

* Removed -xmx VM argument from Explorer's Capsule setup. This helps avoiding out of memory errors.

* Node will now gracefully fail to start if one of the required ports is already in use.

* Fixed incorrect exception handling in ``NodeVaultService._query()``.

* Avoided a memory leak deriving from incorrect MappedSchema caching strategy.

* Fix CORDA-1403 where a property of a class that implemented a generic interface could not be deserialised in
  a factory without a serialiser as the subtype check for the class instance failed. Fix is to compare the raw
  type.

* Fix CORDA-1229. Setter-based serialization was broken with generic types when the property was stored
  as the raw type, List for example.

* Table name with a typo changed from ``NODE_ATTCHMENTS_CONTRACTS`` to ``NODE_ATTACHMENTS_CONTRACTS``.

.. _changelog_v3.2:

Version 3.2
-----------

* Doorman and NetworkMap URLs can now be configured individually rather than being assumed to be
  the same server. Current ``compatibilityZoneURL`` configurations remain valid. See both :doc:`corda-configuration-file`
  and :doc:`permissioning` for details.

* Table name with a typo changed from ``NODE_ATTCHMENTS_CONTRACTS`` to ``NODE_ATTACHMENTS_CONTRACTS``.

.. _changelog_v3.1:

Version 3.1
-----------

* Update the fast-classpath-scanner dependent library version from 2.0.21 to 2.12.3

  .. note:: Whilst this is not the latest version of this library, that being 2.18.1 at time of writing, versions later
  than 2.12.3 (including 2.12.4) exhibit a different issue.

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

* Due to a security risk, the `conflict` property has been removed from `NotaryError.Conflict` error object. It has been replaced
  with `consumedStates` instead. The new property no longer specifies the original requesting party and transaction id for
  a consumed state. Instead, only the hash of the transaction id is revealed. For more details why this change had to be
  made please refer to the release notes.

* Added ``NetworkMapCache.getNodesByLegalName`` for querying nodes belonging to a distributed service such as a notary cluster
  where they all share a common identity. ``NetworkMapCache.getNodeByLegalName`` has been tightened to throw if more than
  one node with the legal name is found.

* Introduced Flow Draining mode, in which a node continues executing existing flows, but does not start new. This is to support graceful node shutdown/restarts.
  In particular, when this mode is on, new flows through RPC will be rejected, scheduled flows will be ignored, and initial session messages will not be consumed.
  This will ensure that the number of checkpoints will strictly diminish with time, allowing for a clean shutdown.

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

* Database schema changes - an H2 database instance of Corda 1.0 and 2.0 cannot be reused for Corda 3.0, listed changes for Vault and Finance module:

    * ``NODE_TRANSACTIONS``:
       column ``"TRANSACTION”`` renamed to ``TRANSACTION_VALUE``, serialization format of BLOB stored in the column has changed to AMQP
    * ``VAULT_STATES``:
       column ``CONTRACT_STATE`` removed
    * ``VAULT_FUNGIBLE_STATES``:
        column ``ISSUER_REFERENCE`` renamed to ``ISSUER_REF`` and the field size increased
    * ``"VAULTSCHEMAV1$VAULTFUNGIBLESTATES_PARTICIPANTS"``:
        table renamed to ``VAULT_FUNGIBLE_STATES_PARTS``,
        column ``"VAULTSCHEMAV1$VAULTFUNGIBLESTATES_OUTPUT_INDEX"`` renamed to ``OUTPUT_INDEX``,
        column ``"VAULTSCHEMAV1$VAULTFUNGIBLESTATES_TRANSACTION_ID"`` renamed to ``TRANSACTION_ID``
    * ``VAULT_LINEAR_STATES``:
        type of column ``"UUID"`` changed from ``VARBINARY`` to ``VARCHAR(255)`` - select varbinary column as ``CAST("UUID" AS UUID)`` to get UUID in varchar format
    * ``"VAULTSCHEMAV1$VAULTLINEARSTATES_PARTICIPANTS"``:
        table renamed to ``VAULT_LINEAR_STATES_PARTS``,
        column ``"VAULTSCHEMAV1$VAULTLINEARSTATES_OUTPUT_INDEX"`` renamed to ``OUTPUT_INDEX``,
        column ``"VAULTSCHEMAV1$VAULTLINEARSTATES_TRANSACTION_ID"`` renamed to ``TRANSACTION_ID``
    * ``contract_cash_states``:
        columns storing Base58 representation of the serialised public key (e.g. ``issuer_key``) were changed to store Base58 representation of SHA-256 of public key prefixed with `DL`
    * ``contract_cp_states``:
        table renamed to ``cp_states``, column changes as for ``contract_cash_states``

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
     "nodeInfo-*". This can be copied to every node's ``additional-node-infos`` directory that is part of the network.

   * Cordform (which is the ``deployNodes`` gradle task) does this copying automatically for the demos. The ``NetworkMap``
     parameter is no longer needed.

   * For test deployments we've introduced a bootstrapping tool (see :doc:`setting-up-a-corda-network`).

   * ``extraAdvertisedServiceIds``, ``notaryNodeAddress``, ``notaryClusterAddresses`` and ``bftSMaRt`` configs have been
     removed. The configuration of notaries has been simplified into a single ``notary`` config object. See
     :doc:`corda-configuration-file` for more details.

   * Introducing the concept of network parameters which are a set of constants which all nodes on a network must agree on
     to correctly interop. These can be retrieved from ``ServiceHub.networkParameters``.

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

* Changed the AMQP serialiser to use the oficially assigned R3 identifier rather than a placeholder.

* The ``ReceiveTransactionFlow`` can now be told to record the transaction at the same time as receiving it. Using this
  feature, better support for observer/regulator nodes has been added. See :doc:`tutorial-observer-nodes`.

* Added an overload of ``TransactionWithSignatures.verifySignaturesExcept`` which takes in a collection of ``PublicKey``s.

* ``DriverDSLExposedInterface`` has been renamed to ``DriverDSL`` and the ``waitForAllNodesToFinish()`` method has instead
  become a parameter on driver creation.

* Values for the ``database.transactionIsolationLevel`` config now follow the ``java.sql.Connection`` int constants but
  without the "TRANSACTION_" prefix, i.e. "NONE", "READ_UNCOMMITTED", etc.

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
