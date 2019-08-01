package net.corda.node.internal

import net.corda.core.contracts.*
import net.corda.core.cordapp.CordappProvider
import net.corda.core.internal.BasicVerifier
import net.corda.core.internal.SerializedStateAndRef
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.NetworkParametersService
import net.corda.core.node.services.TransactionStorage
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.transactions.WireTransaction.Companion.resolveStateRefBinaryComponent
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.Whitelist
import net.corda.djvm.source.ApiSource
import net.corda.djvm.source.UserPathSource
import net.corda.djvm.source.UserSource
import net.corda.node.internal.djvm.DeterministicVerifier
import java.net.URLClassLoader

data class ServicesForResolutionImpl(
        override val identityService: IdentityService,
        override val attachments: AttachmentStorage,
        override val cordappProvider: CordappProvider,
        override val networkParametersService: NetworkParametersService,
        private val validatedTransactions: TransactionStorage,
        private val djvmBootstrapSource: ApiSource,
        private val djvmCordaSource: UserSource?
) : ServicesForResolution {
    override val networkParameters: NetworkParameters get() = networkParametersService.lookup(networkParametersService.currentHash) ?:
            throw IllegalArgumentException("No current parameters in network parameters storage")

    @Throws(TransactionResolutionException::class)
    override fun loadState(stateRef: StateRef): TransactionState<*> {
        val stx = validatedTransactions.getTransaction(stateRef.txhash) ?: throw TransactionResolutionException(stateRef.txhash)
        return stx.resolveBaseTransaction(this).outputs[stateRef.index]
    }

    @Throws(TransactionResolutionException::class)
    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return stateRefs.groupBy { it.txhash }.flatMap {
            val stx = validatedTransactions.getTransaction(it.key) ?: throw TransactionResolutionException(it.key)
            val baseTx = stx.resolveBaseTransaction(this)
            it.value.map { ref -> StateAndRef(baseTx.outputs[ref.index], ref) }
        }.toSet()
    }

    @Throws(TransactionResolutionException::class, AttachmentResolutionException::class)
    override fun loadContractAttachment(stateRef: StateRef): Attachment {
        // We may need to recursively chase transactions if there are notary changes.
        fun inner(stateRef: StateRef, forContractClassName: String?): Attachment {
            val ctx = validatedTransactions.getTransaction(stateRef.txhash)?.coreTransaction
                    ?: throw TransactionResolutionException(stateRef.txhash)
            when (ctx) {
                is WireTransaction -> {
                    val transactionState = ctx.outRef<ContractState>(stateRef.index).state
                    for (attachmentId in ctx.attachments) {
                        val attachment = attachments.openAttachment(attachmentId)
                        if (attachment is ContractAttachment && (forContractClassName ?: transactionState.contract) in attachment.allContracts) {
                            return attachment
                        }
                    }
                    throw AttachmentResolutionException(stateRef.txhash)
                }
                is ContractUpgradeWireTransaction -> {
                    return attachments.openAttachment(ctx.upgradedContractAttachmentId) ?: throw AttachmentResolutionException(stateRef.txhash)
                }
                is NotaryChangeWireTransaction -> {
                    val transactionState = SerializedStateAndRef(resolveStateRefBinaryComponent(stateRef, this)!!, stateRef).toStateAndRef().state
                    // TODO: check only one (or until one is resolved successfully), max recursive invocations check?
                    return ctx.inputs.map { inner(it, transactionState.contract) }.firstOrNull() ?: throw AttachmentResolutionException(stateRef.txhash)
                }
                else -> throw UnsupportedOperationException("Attempting to resolve attachment for index ${stateRef.index} of a ${ctx.javaClass} transaction. This is not supported.")
            }
        }
        return inner(stateRef, null)
    }

    override fun specialise(ltx: LedgerTransaction): LedgerTransaction {
        // Do nothing unless we have Corda's deterministic libraries.
        val cordaSource = djvmCordaSource ?: return ltx

        // Specialise the LedgerTransaction here so that
        // contracts are verified inside the DJVM!
        return ltx.specialise { tx, cl ->
            (cl as? URLClassLoader)?.run { DeterministicVerifier(tx, cl, createSandbox(cordaSource, cl)) } ?: BasicVerifier(tx, cl)
        }
    }

    private fun createSandbox(cordaSource: UserSource, classLoader: URLClassLoader): AnalysisConfiguration {
        return AnalysisConfiguration.createRoot(
            userSource = cordaSource,
            whitelist = Whitelist.MINIMAL,
            bootstrapSource = djvmBootstrapSource
        ).createChild(UserPathSource(classLoader.urLs), null)
    }
}
