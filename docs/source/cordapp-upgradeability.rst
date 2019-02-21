CorDapp Upgradeability Guarantees
=================================

Corda 4.0
---------

Corda 4 introduces a number of advanced features (such as signature constraints), and data security model improvements (such as attachments
trust checking and classloader isolation of contract attachments for transaction building and verification).

The following guarantees are made for CorDapps running on Corda 4.0

- Compliant CorDapps compiled with previous versions of Corda (from 3.0) will execute without change on Corda 4.0

  .. note:: by "compliant", we mean CorDapps that do not utilise Corda internal, non-stable or other non-committed public Corda APIs.

  Recommendation: security hardening changes in flow processing, specifically the ``FinalityFlow``, recommend upgrading existing CorDapp
  receiver flows to use the new APIs. See :ref:`cordapp_upgrade_finality_flow_ref` for more information.

- CorDapp Contract states generated on ledger using hash constraints (introduced in Corda 1.0) and CZ whitelisted constraints (introduced in Corda 3.0)
  are consumable by CorDapps built on Corda 4. However, you may not combine inputs constrained by different types (eg. cannot mix hash and CZ whitelist
  and signature constrained states) in the same transaction. Note you can combine multiple inputs constrained by the same type (many of hash or
  CZ whitelist or signature) in the same transaction.
  An exception to this rule is where you may have subsequently CZ whitelisted a Jar previously used to issue hash constrained states (eg. the original hash-constrained states
  and new CZ-whitelisted constrained states may be combined in a new transaction in this scenario because they resolve to the same original hash).

- CorDapp Contract states generated on ledger using hash constraints are not migratable to signature constraints in this release.

- CorDapp Contract states generated on ledger using CZ whitelisted constraints are migratable to signature constraints using a manual process
  that requires programmatic code changes. See :ref:`constraints_whitelist_to_signature_ref` for more information.

- Explicit Contract Upgrades are only supported for hash and CZ whitelisted constraint types. See :ref:`explicit_contract_upgrades_ref` for more information.

- CorDapp contract attachments are not trusted from remote peers over the p2p network for the purpose of transaction verification.
  A node operator must locally install *all* versions of a Contract attachment to be able to resolve a chain of contract states from its original version.
  The RPC ``uploadAttachment`` mechanism can be used to achieve this (as well as conventional loading of a CorDapp by installing it in the nodes /cordapp directory).
  See :ref:`cordapp_install_ref` and :ref:`cordapp_contract_attachments_ref` for more information.

- CorDapp contract attachment classloader isolation has some important side-effects and edge cases to consider:

  1. Contract attachments should include all 3rd party library dependencies in the same packaged JAR - we call this a "Fat JAR",
     meaning that all dependencies are resolvable by the classloader by only loading a single Jar.
  2. Contract attachments that depend on other Contract attachments are currently supported in so far as the Attachments Classloader
     will attempt to resolve any external dependencies from the node's application classloader. It is thus paramount that dependent Contract
     Attachments are loaded upon node startup from the respective /cordapps directory.

- Rolling upgrades are partially supported.
  A Node operator may choose to manually upload (via the RPC attachments uploader mechanism) a later version of a Contract Attachment than
  the version their node is currently using for the purposes of transaction verification (received from remote peers). However, they will only
  be able to build new transactions with the version that is currently loaded (installed from the nodes /cordapps directory) in their node.

- Finance CorDapp (v4)
  Whilst experimental, our test coverage has confirmed that states generated with the Finance CorDapp are interchangeable across Open Source
  and Enterprise distributions. This has been made possible by releasing a single 4.0 version of the Finance Contracts CorDapp.
  Please note the Finance application will be superseded shortly by the new Tokens SDK (https://github.com/corda/token-sdk)

Corda 4.1
---------

The following additional capabilities are under consideration for delivery in a follow-up release to Corda 4.0:

- CorDapp contract states issued with different constraint types will be consumable within the same transaction.
  eg. no longer need to consume hash, CZ whitelist and signature constraints in isolation.

- CorDapp Contract states generated on ledger using hash constraints will be automatically migrated to signature constraints when building new transactions
  where the latest installed contract Jar is signed as per :ref:`CorDapp Jar signing <cordapp_build_system_signing_cordapp_jar_ref>`.

- CorDapp Contract states generated on ledger using CZ whitelisted constraints will be automatically migrated to signature constraints when building new transactions
  where the latest installed contract Jar is signed as per :ref:`CorDapp Jar signing <cordapp_build_system_signing_cordapp_jar_ref>`.

- Explicit Contract Upgrades will be supported for all constraint types: hash, CZ whitelisted and signature.
  In practice, it should only be necessary to upgrade from hash or CZ whitelisted to new signature constrained contract types.
  signature constrained Contracts are upgradeable seamlessly (through built in serialization and code signing controls) without requiring explicit upgrades.

- Contract attachments will be able to explicitly declare their dependencies on other Contract attachments such that these are automatically
  loaded by the Attachments Classloader (rendering the 4.0 fallback to application classloader mechanism redundant).
  This improved modularity removes the need to "Fat JAR" all dependencies together in a single jar.

- Rolling upgrades will be fully supported.
  A Node operator will be able to pre-register (by hash or code signing public key) versions of CorDapps they are not yet ready to install locally,
  but wish to use for the purposes of transaction verification with peers running later versions of a CorDapp.

.. note:: Trusted downloading of contract attachments from remote peers will not be integrated until secure JVM sand-boxing is available.

Corda Enterprise concerns
-------------------------

- CorDapps compiled with the OS version of Corda 4.0 will execute without change on Enterprise Corda 4.0.
  The reverse is not guaranteed. Whilst the Public APIs are currently identical, R3 may introduce Enterprise-specific Public APIs for
  advanced CorDapp functionality, therefore invalidating the ability to execute on Open Source nodes.
  Wire-compatibility and ABI stability is maintained.

- The Finance Contract CorDapp is only available in the Open Source distribution to ensure uniqueness and singularity of JAR "hash".
  This is necessary to ensure there is only one unique version of the Finance Contract JAR such that Open Source and Enterprise nodes
  can transact finance contract states interchangeably without classloading and constraints failures.