Changelog
=========

Here are brief summaries of what's changed between each snapshot release. This includes guidance on how to upgrade code
from the previous milestone release.

UNRELEASED
----------

* Changes in ``NodeInfo``:

   * ``PhysicalLocation`` was renamed to ``WorldMapLocation`` to emphasise that it doesn't need to map to a truly physical
     location of the node server.
   * Slots for multiple IP addresses and ``legalIdentitiesAndCert``s were introduced. Addresses are no longer of type
     ``SingleMessageRecipient``, but of ``NetworkHostAndPort``.

* ``ServiceHub.storageService`` has been removed. ``attachments`` and ``validatedTransactions`` are now direct members of
  ``ServiceHub``.

* Mock identity constants used in tests, such as ``ALICE``, ``BOB``, ``DUMMY_NOTARY``, have moved to ``net.corda.testing``
  in the ``test-utils`` module.

* ``DummyContract``, ``DummyContractV2``, ``DummyLinearContract`` and ``DummyState`` have moved to ``net.corda.testing.contracts``
  in the ``test-utils`` modules.

* In Java, ``QueryCriteriaUtilsKt`` has moved to ``QueryCriteriaUtils``. Also ``and`` and ``or`` are now instance methods
  of ``QueryCrtieria``.

* ``random63BitValue()`` has moved to ``CryptoUtils``

* Added additional common Sort attributes (see ``Sort.CommandStateAttribute``) for use in Vault Query criteria
  to include STATE_REF, STATE_REF_TXN_ID, STATE_REF_INDEX

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

   * See :doc:`vault-query` for full details and code samples of using the new Vault Query service.

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

Milestone 12
------------

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
           operation.
         * ``ServiceHub.legalIdentityKey`` no longer returns a ``KeyPair``, it instead returns just the ``PublicKey`` portion of this pair.
       The ``ServiceHub.notaryIdentityKey`` has changed similarly. The goal of this change is to keep private keys
           encapsulated and away from most flow code/Java code, so that the private key material can be stored in HSMs
           and other key management devices.
     * The ``KeyManagementService`` no longer provides any mechanism to request the node's ``PrivateKey`` objects directly.
       Instead signature creation occurs in the ``KeyManagementService.sign``, with the ``PublicKey`` used to indicate
       which of the node's keypairs to use. This lookup also works for ``CompositeKey`` scenarios
       and the service will search for a leaf key hosted on the node.
     * The ``KeyManagementService.freshKey`` method now returns only the ``PublicKey`` portion of the newly generated ``KeyPair``
       with the ``PrivateKey`` kept internally to the service.
     * Flows which used to acquire a node's ``KeyPair``, typically via ``ServiceHub.legalIdentityKey``,
       should instead use the helper methods on ``ServiceHub``. In particular to freeze a ``TransactionBuilder`` and
       generate an initial partially signed ``SignedTransaction`` the flow should use ``ServiceHub.signInitialTransaction``.
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
          imposible to create ``CompositeKey`` with only one leaf.

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

    * You can download Corda DemoBench from `here <https://www.corda.net/downloads/>`_

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
  some financial situations. See :ref:`notary-demo` to try it out. A BFT notary will be added later.

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
      are present in many state definitions. Read more about :doc:`persistence`.
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
      is the same as the InterLedger Crypto-Conditions spec, which should aid interop in future. Read more about
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
    * You can pick between validating and non-validating notaries, these let you select your privacy/robustness tradeoff.

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
* :doc:`core-types`
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
