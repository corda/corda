CorDapp Upgradeability Guarantees
=================================

Corda 4.0
---------

Corda 4 introduces a number of advanced features (such as signature constraints), and data security model improvements (such as attachments
trust checking and classloader isolation of contract attachments for transaction building and verification).

The following guarantees are made for CorDapps running on Corda 4.0

- CorDapps compiled with previous versions of Corda (from 1.0) will execute without change on Corda 4.0

  Recommendation: security hardening changes in flow processing, specifically the ``FinalityFlow``, recommend upgrading existing CorDapp
  receiver flows to use the new APIs. See :ref:`cordapp_upgrade_finality_flow_ref` for more information.

- CorDapp Contract states generated on ledger using Hash constraints (introduced in Corda 1.0) and CZ Whitelisted constraints (introduced in Corda 3.0)
  are consumable by CorDapps built on Corda 4 (by default these will use Signature constraints) but may not be combined as inputs into the
  same transaction. A transaction may consume multiple states of the same constraint type but not combined.

- CorDapp Contract states generated on ledger using Hash constraints are not migratable to Signature constraints in this release.

- CorDapp Contract states generated on ledger using CZ Whitelisted constraints are migratable to Signature constraints using a manual process
  that requires programmatic code changes. See :ref:`constraints_whitelist_to_signature_ref` for more information.

- Explicit Contract Upgrades are only supported for Hash and CZ Whitelisted constraint types. See :ref:`explicit_contract_upgrades_ref` for more information.

- CorDapp contract attachments are no longer trusted from remote peers over the p2p network for the purpose of transaction verification.
  The implication of this data security hardening step is that a Node operator must locally install *all* versions of a Contract attachment
  to be able to resolve a chain of contract states from its original version.
  The RPC ``UploadAttachment`` mechanism can be used to achieve this (as well as conventional loading of a CorDapp by installing it in the nodes /cordapp directory).
  See :ref:`cordapp_install_ref` and :ref:`cordapp_contract_attachments_ref` for more information.

- CorDapp contract attachment classloader isolation has some important side-effects and edge cases to consider:

  1. Contract attachments should include all 3rd party library dependencies in the same packaged JAR.
  2. Contract attachments that depend on other Contract attachments are currently supported in so far as the Attachments Classloader
     will attempt to resolve any external dependencies from the Nodes application classloader. It is thus paramount that dependent Contract
     Attachments are loaded upon node startup from the respective /cordapps directory.

- Rolling upgrades are partially supported.
  A Node operator may choose to manually upload a later version of a Contract Attachment than the version their node is currently using
  for the purposes of transaction verification (from remote peers). They will only be able to build new transactions with the version
  that is currently loaded in their node (as installed from the nodes /cordapps directory)

- Finance CorDapp
  Whilst experimental, we guarantee that states generated with the Finance CorDapp are interchangeable across Open Source and Enterprise
  distributions. This has been made possible by releasing a single version of the Finance Contracts CorDapp.
  Please note the Finance application will be superseded shortly by the new Tokens SDK (https://github.com/corda/token-sdk)

Corda 4.1
---------

The following guarantees will be delivered in a close follow-up release to Corda 4.0:

- CorDapp contract states issued with different constraint types will be consumable within the same transaction.
  eg. no longer need to consume hash, CZ whitelist and signature constraints in isolation.

- CorDapp Contract states generated on ledger using Hash constraints will be automatically migrated to Signature constraints when building new transactions.

- CorDapp Contract states generated on ledger using CZ Whitelisted constraints will be automatically migrated to Signature constraints when building new transactions.

- Explicit Contract Upgrades will be supported for all constraint types: Hash, CZ Whitelisted and Signature.
  In practice, it should only be necessary to upgrade from hash or CZ whitelisted to new signature constrained contract types.
  Signature constrained Contracts are upgradeable seamlessly (through built in serialization and code signing controls) without requiring explicit upgrades.

- Contract attachments will be able to explicitly declare their dependencies on other Contract attachments such that these are automatically
  loaded by the Attachments Classloader (rendering the 4.0 fallback to application classloader mechanism redundant).

- Rolling upgrades will be fully supported.
  A Node operator will be able to pre-register (by hash or code signing public key) versions of CorDapps they are not yet ready to install locally,
  but wish to use for the purposes of transaction verification with peers running later versions of a CorDapp.

.. note:: Trusted downloading of contract attachments from remote peers will not be available until secure JVM sand-boxing is available.

Corda Enterprise concerns
-------------------------

- CorDapps compiled with the OS version of Corda 4.0 will execute without change on Enterprise Corda 4.0
  The reverse is not guaranteed. Whilst the Public API's are currently identical, R3 may introduce Enterprise-specific Public API's for
  advanced CorDapp functionality, therefore invalidating the ability to execute on Open Source nodes.
  Wire-compatibility and ABI stability is maintained.

- The Finance Contract CorDapp is only available in the Open Source distribution to ensure uniqueness and singularity of JAR "hash".
  This is necessary to ensure there is only one unique version of the Finance Contract JAR such that Open Source and Enterprise nodes
  can transact finance contract states interchangeably without classloading and constraints failures.