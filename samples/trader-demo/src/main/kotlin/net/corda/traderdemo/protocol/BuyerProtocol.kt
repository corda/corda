package net.corda.traderdemo.protocol

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.CommercialPaper
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionGraphSearch
import net.corda.core.crypto.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.PluginServiceHub
import net.corda.core.protocols.ProtocolLogic
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.protocols.TwoPartyTradeProtocol
import java.nio.file.Path
import java.util.*

class  BuyerProtocol(val otherParty: Party,
                    private val attachmentsPath: Path,
                    override val progressTracker: ProgressTracker = ProgressTracker(STARTING_BUY)) : ProtocolLogic<Unit>() {

    object STARTING_BUY : ProgressTracker.Step("Seller connected, purchasing commercial paper asset")

    class Service(services: PluginServiceHub) : SingletonSerializeAsToken() {
        init {
            // Buyer will fetch the attachment from the seller automatically when it resolves the transaction.
            // For demo purposes just extract attachment jars when saved to disk, so the user can explore them.
            val attachmentsPath = (services.storageService.attachments as NodeAttachmentService).let {
                it.automaticallyExtractAttachments = true
                it.storePath
            }
            services.registerProtocolInitiator(SellerProtocol::class) { BuyerProtocol(it, attachmentsPath) }
        }
    }

    @Suspendable
    override fun call() {
        progressTracker.currentStep = STARTING_BUY

        // Receive the offered amount and automatically agree to it (in reality this would be a longer negotiation)
        val amount = receive<Amount<Currency>>(otherParty).unwrap { it }
        val notary: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
        val buyer = TwoPartyTradeProtocol.Buyer(
                otherParty,
                notary.notaryIdentity,
                amount,
                CommercialPaper.State::class.java)

        // This invokes the trading protocol and out pops our finished transaction.
        val tradeTX: SignedTransaction = subProtocol(buyer, shareParentSessions = true)
        // TODO: This should be moved into the protocol itself.
        serviceHub.recordTransactions(listOf(tradeTX))

        logger.info("Purchase complete - we are a happy customer! Final transaction is: " +
                "\n\n${Emoji.renderIfSupported(tradeTX.tx)}")

        logIssuanceAttachment(tradeTX)
        logBalance()
    }

    private fun logBalance() {
        val balances = serviceHub.vaultService.cashBalances.entries.map { "${it.key.currencyCode} ${it.value}" }
        logger.info("Remaining balance: ${balances.joinToString()}")
    }

    private fun logIssuanceAttachment(tradeTX: SignedTransaction) {
        // Find the original CP issuance.
        val search = TransactionGraphSearch(serviceHub.storageService.validatedTransactions, listOf(tradeTX.tx))
        search.query = TransactionGraphSearch.Query(withCommandOfType = CommercialPaper.Commands.Issue::class.java,
                followInputsOfType = CommercialPaper.State::class.java)
        val cpIssuance = search.call().single()

        cpIssuance.attachments.first().let {
            val p = attachmentsPath.toAbsolutePath().resolve("$it.jar")
            logger.info("""

The issuance of the commercial paper came with an attachment. You can find it expanded in this directory:
$p

${Emoji.renderIfSupported(cpIssuance)}""")
        }
    }
}
