Changelog
=========

Here are brief summaries of what's changed between each snapshot release.

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
  been updated to reflect this change.
  Existing CorDapps should be updated with additional calls to the new ``startWebserver()`` interface in their Driver logic (if they use the driver e.g. in integration tests).
  See the IRS demo for an example.

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
      key trees in the ":doc:`transaction-data-types`" article.
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
      repository, where they can then be used like any other JAR. See :doc:`creating-a-cordapp`.

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
* :doc:`transaction-data-types`
* :doc:`consensus`

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
