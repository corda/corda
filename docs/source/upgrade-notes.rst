Upgrade notes
=============

These notes provide helpful instructions to upgrade your Corda Applications (CorDapp's) from previous versions, starting
from Milestone 12 (first public beta), to V1.0

General
-------
Always remember to update the version identifies in your project gradle file:

    ext.corda_release_version = '1.0.0'
    ext.corda_gradle_plugins_version = '1.0.0'

Optionally, it may be necessary to update the version of major dependencies:

    ext.kotlin_version = '1.1.4'
    ext.quasar_version = '0.7.9'

This will be documented in the relevant release notes of the release in question.

Milestone 14
------------

* Flow session deprecations
  ``FlowLogic`` communication has been extensively rewritten to use functions on ``FlowSession`` as the base for communication
  between nodes.
  * Calls to ``send()``, ``receive()`` and ``sendAndReceive()`` on FlowLogic should be replaced with calls
    to the function of the same name on ``FlowSession``. Note that the replacement functions do not take in a destination
    parameter, as this is defined in the session.
  * Initiated flows now take in a ``FlowSession`` instead of ``Party`` in their constructor. If you need to access the
    counterparty identity, it is in the ``counterparty`` property of the flow session.
  See ``FlowSession`` for step by step instructions on porting existing flows to use the new mechanism.

* Missing imports for utility functions.
  Many common types and helper methods have been consolidated into `net.corda.core.utilities` package.
  For example:
    import net.corda.core.crypto.commonName -> import net.corda.core.utilities.commonName
    import net.corda.core.crypto.toBase58String -> import net.corda.core.utilities.toBase58String
    import net.corda.core.getOrThrow -> import net.corda.core.utilities.getOrThrow
  Use the automatic imports feature of IntelliJ to intelligently resolve the new imports.

* Missing API methods on CordaRPCOps interface.
  Calls to `verifiedTransactionsFeed()` and `verifiedTransactions()` have been replaced with:
  `internalVerifiedTransactionsSnapshot()` and `internalVerifiedTransactionsFeed()` respectively

* Missing Contract override.
  The contract interace attribute `legalContractReference` has been removed, and replaced by
  the annotation `@LegalProseReference(uri = "<URI>")`

* Unresolved reference.
  `AuthenticatedObject` is replaced by `CommandWithParties`

* Missing imports for contract types.
  CommercialPaper and Cash are now contained within the `finance` module, as are associated helpers functions.
  For example:
    import net.corda.contracts.ICommercialPaperState -> import net.corda.finance.contracts.ICommercialPaperState
    import net.corda.contracts.asset.sumCashBy -> import net.corda.finance.utils.sumCashBy
    import net.corda.core.contracts.DOLLARS -> import net.corda.finance.DOLLARS
    import net.corda.core.contracts.`issued by` -> import net.corda.finance.`issued by`
    import net.corda.contracts.asset.Cash -> import net.corda.finance.contracts.asset.Cash

* MockNetwork is no longer resolvable.
  A new test driver module dependency needs to be including in your project: `corda-node-driver`.
  To continue using the mock network for testing, replace
  `testCompile "net.corda:corda-test-utils:$corda_release_version"` with `testCompile "net.corda:corda-node-driver:$corda_release_version"`

* Overrides nothing: isRelevant in LinearState.
  Removed the concept of relevancy from ``LinearState``. The ``ContractState``'s relevancy to the vault can be determined
  by the flow context, the vault will process any transaction from a flow which is not derived from transaction resolution verification.

* Clauses are no longer available.

* FungibleAsset interface simplification.
  The ``FungibleAsset`` interface has been made simpler. The ``Commands`` grouping interface
  that included the ``Move``, ``Issue`` and ``Exit`` interfaces have all been removed, while the ``move`` function has
  been renamed to ``withNewOwnerAndAmount`` to be consistent with the ``withNewOwner`` function of the ``OwnableState``.
  The following errors may be reported:
  - Override nothing (FungibleAsset): `move`
  - Not a subtype of overridden FungibleAsset: `withNewOwner`
  - no longer need to override `override val contractHash: SecureHash? = null`
  - need to override `override val contract: Class<out Contract>? = null`

* Missing flow import.
  import net.corda.core.flows.ResolveTransactionsFlow -> import net.corda.core.internal.ResolveTransactionsFlow

* `FinalityFlow` now returns a single `SignedTransaction`, instead of a `List<SignedTransaction>`

* `getAnyNotary` is gone - use `serviceHub.networkMapCache.notaryIdentities[0]` instead

* serviceHub.myInfo.legalIdentity no longer exists, use the ourIdentity property of the flow instead.
  `FlowLogic.ourIdentity` has been introduced as a shortcut for retrieving our identity in a flow

* `args[0].parseNetworkHostAndPort()` becomes `NetworkHostAndPort.parse(args[0])`

* `ServiceHub.networkMapUpdates` is replaced by `ServiceHub.networkMapFeed`

* `ServiceHub.partyFromX500Name` is replaced by `ServiceHub.wellKnownPartyFromX500Name`

* txBuilder.toLedgerTransaction() now requires a serviceHub parameter.
  Used by the new Contract Constraints functionality to validate and resolve attachments.

* Moved ``finance`` gradle project files into a ``net.corda.finance`` package namespace.
  This may require adjusting imports of Cash flow references and also of ``StartFlow`` permission in ``gradle.build`` files.
  Associated flows (cash, two party trade, two part deal) must now be imported from this package.

* There is no longer a `NodeInfo.advertisedServices` property.

Milestone 13
------------

* TransactionBuilder changes.
  Use convenience class `StateAndContract` instead of `TransactionBuilder.withItems()` for passing
  around a state and its contract.

* MockNetwork is no longer resolvable.
  A new test driver module dependency needs to be including in your project: `corda-node-driver`.
  Add the following to your application's gradle dependencies:
        testCompile "net.corda:corda-node-driver:$corda_release_version"

* ServiceHub API method changes.
  `services.networkMapUpdates().justSnapshot` becomes `services.networkMapSnapshot()`

* Transaction building DSL changes:
  - now need to explicitly pass the ContractClassName into all inputs and outputs.
  - `ContractClassName` refers to the class containing the “verifier” method.

* Contract verify method signature change.
  `override fun verify(tx: TransactionForContract)` becomes `override fun verify(tx: LedgerTransaction)`

* No longer need to override Contract `contract()` function.

* No longer need to define `CordaPluginRegistry` and configure `requiredSchemas`
  Custom contract schemas are automatically detected at startup time by class path scanning.
  For testing purposes, use the `SchemaService` method to register new custom schemas:
    `services.schemaService.registerCustomSchemas(setOf(YoSchemaV1))`

* Party names are now `CordaX500Name`, not `X500Name`
  `CordaX500Name` specifies a predefined set of mandatory (organisation, locality, country)
  and optional fields (commonName, organisationUnit, state) with validation checking.
  Use new builder CordaX500Name.build(X500Name(target)) or, preferably, explicitly define X500Name parameters using
  `CordaX500Name` constructor.

* MockNetwork Testing.
  Mock nodes in node tests are now of type `StartedNode<MockNode>`, rather than `MockNode`
  MockNetwork now returns a BasketOf(<StartedNode<MockNode>>)
  Must call internals on StartedNode to get MockNode:
    a = nodes.partyNodes[0].internals
    b = nodes.partyNodes[1].internals

* Host and Port change.
  Use string helper function `parseNetworkHostAndPort()` to parse a URL on startup.
   eg. val hostAndPort = args[0].parseNetworkHostAndPort()`

* The node driver parameters for starting a node have been reordered, and the node’s name needs to be given as an
  `CordaX500Name`, instead of using `getX509Name`


Milestone 12 - First Public Beta
--------------------------------

* Gradle dependency reference changes.
  Module name has changed to include `corda` in the artifacts jar name:
  For example:
    compile "net.corda:core:$corda_release_version" -> compile "net.corda:corda-core:$corda_release_version"
    compile "net.corda:finance:$corda_release_version" -> compile "net.corda:corda-finance:$corda_release_version"
    compile "net.corda:jackson:$corda_release_version" -> compile "net.corda:corda-jackson:$corda_release_version"
    compile "net.corda:node:$corda_release_version" -> compile "net.corda:corda-node:$corda_release_version"
    compile "net.corda:rpc:$corda_release_version" -> compile "net.corda:corda-rpc:$corda_release_version"

* ServiceHub API changes.
  `services.networkMapUpdates()` becomes `services.networkMapFeed()`
  `services.getCashBalances()` becomes a helper method within the `finance` module contracts package: `net.corda.finance.contracts.getCashBalances`

* Financial contract asssets (Cash, CommercialPaper, Obligations) are now a standalone CorDapp within the `finance` module.
  Need to import from respective package within `finance` module:
    eg. net.corda.finance.contracts.asset.Cash
  Likewise, need to import associated asset flows from respective package within `finance` module:
    eg. net.corda.finance.flows.CashIssueFlow
        net.corda.finance.flows.CashIssueAndPaymentFlow
        net.corda.finance.flows.CashExitFlow

* Transaction building
  You no longer need to specify the type of a `TransactionBuilder` as `TransactionType.General`
  `TransactionType.General.Builder(notary)` becomes `TransactionBuilder(notary)`