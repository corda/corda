package net.corda.node.services.transactions

import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotarisationPayload
import net.corda.core.flows.NotaryError
import net.corda.core.identity.Party
import net.corda.core.internal.notary.NotaryInternalException
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.node.NetworkParameters
import net.corda.core.transactions.ContractUpgradeFilteredTransaction
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import java.time.Duration

/**
 * The received transaction is not checked for contract-validity, as that would require fully
 * resolving it into a [TransactionForVerification], for which the caller would have to reveal the whole transaction
 * history chain.
 * As a result, the Notary _will commit invalid transactions_ as well, but as it also records the identity of
 * the caller, it is possible to raise a dispute and verify the validity of the transaction and subsequently
 * undo the commit of the input states (the exact mechanism still needs to be worked out).
 */
class NonValidatingNotaryFlow(otherSideSession: FlowSession, service: SinglePartyNotaryService, etaThreshold: Duration) : NotaryServiceFlow(otherSideSession, service, etaThreshold) {
    private val minPlatformVersion get() = serviceHub.networkParameters.minimumPlatformVersion

    override fun extractParts(requestPayload: NotarisationPayload): TransactionParts {
        val tx = requestPayload.coreTransaction
        return when (tx) {
            is FilteredTransaction -> TransactionParts(tx.id, tx.inputs, tx.timeWindow, tx.notary, tx.references, networkParametersHash = tx.networkParametersHash)
            is ContractUpgradeFilteredTransaction,
            is NotaryChangeWireTransaction -> TransactionParts(tx.id, tx.inputs, null, tx.notary, networkParametersHash = tx.networkParametersHash)
            else -> throw unexpectedTransactionType(tx)
        }
    }

    override fun verifyTransaction(requestPayload: NotarisationPayload) {
        val tx = requestPayload.coreTransaction
        try {
            when (tx) {
                is FilteredTransaction -> {
                    tx.apply {
                        verify()
                        checkAllComponentsVisible(ComponentGroupEnum.INPUTS_GROUP)
                        checkAllComponentsVisible(ComponentGroupEnum.TIMEWINDOW_GROUP)
                        checkAllComponentsVisible(ComponentGroupEnum.REFERENCES_GROUP)
                        if (minPlatformVersion >= 4) checkAllComponentsVisible(ComponentGroupEnum.PARAMETERS_GROUP)
                    }
                    val notary = tx.notary ?: throw IllegalArgumentException("Transaction does not specify a notary.")
                    checkNotaryWhitelisted(notary, tx.networkParametersHash)
                }
                is ContractUpgradeFilteredTransaction -> {
                    checkNotaryWhitelisted(tx.notary, tx.networkParametersHash)
                }
                is NotaryChangeWireTransaction -> {
                    checkNotaryWhitelisted(tx.newNotary, tx.networkParametersHash)
                }
                else -> throw unexpectedTransactionType(tx)
            }
        } catch (e: Exception) {
            throw NotaryInternalException(NotaryError.TransactionInvalid(e))
        }
    }

    /** Make sure the transaction notary is part of the network parameter whitelist. */
    private fun checkNotaryWhitelisted(notary: Party, attachedParameterHash: SecureHash?) {
        if (minPlatformVersion >= 4) {
            // Expecting network parameters to be attached for platform version 4 or later.
            if (attachedParameterHash == null) {
                throw IllegalArgumentException("Transaction must contain network parameters.")
            }
            val attachedParameters = serviceHub.networkParametersService.lookup(attachedParameterHash)
                    ?: throw IllegalStateException("Unable to resolve network parameters from hash: $attachedParameterHash")

            checkInWhitelist(attachedParameters, notary)
        } else {
            // Using current network parameters for platform versions 3 or earlier.
            val defaultParams = with(serviceHub.networkParametersService) {
                lookup(currentHash)
                        ?: throw IllegalStateException("Unable to verify whether the notary $notary is whitelisted: current network parameters not set.")
            }
            checkInWhitelist(defaultParams, notary)
        }
    }

    private fun checkInWhitelist(networkParameters: NetworkParameters, notary: Party) {
        val notaryWhitelist = networkParameters.notaries.map { it.identity }

        check(notary in notaryWhitelist) {
            "Notary specified by the transaction ($notary) is not on the network parameter whitelist: ${notaryWhitelist.joinToString()}"
        }
    }

    private fun unexpectedTransactionType(tx: CoreTransaction): IllegalArgumentException {
        return IllegalArgumentException("Received unexpected transaction type: ${tx::class.java.simpleName}," +
                "expected either ${FilteredTransaction::class.java.simpleName} or ${NotaryChangeWireTransaction::class.java.simpleName}")
    }
}
