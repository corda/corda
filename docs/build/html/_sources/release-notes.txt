Release notes
=============

Here are brief summaries of what's changed between each snapshot release.

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

* The transaction types (Signed, Wire, LedgerTransaction) have moved to ``com.r3corda.core.transactions``. You can
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
      relevant in the wake of TheDAO hack.
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

* The cash contract has moved from ``com.r3corda.contracts`` to ``com.r3corda.contracts.cash``
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
