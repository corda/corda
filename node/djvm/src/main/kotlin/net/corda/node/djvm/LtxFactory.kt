@file:JvmName("LtxConstants")
package net.corda.node.djvm

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.NetworkParameters
import net.corda.core.transactions.LedgerTransaction
import java.util.function.Function

private const val TX_INPUTS = 0
private const val TX_OUTPUTS = 1
private const val TX_COMMANDS = 2
private const val TX_ATTACHMENTS = 3
private const val TX_ID = 4
private const val TX_NOTARY = 5
private const val TX_TIME_WINDOW = 6
private const val TX_PRIVACY_SALT = 7
private const val TX_NETWORK_PARAMETERS = 8
private const val TX_REFERENCES = 9
private const val TX_DIGEST_SERVICE = 10

class LtxFactory : Function<Array<out Any?>, LedgerTransaction> {

    @Suppress("unchecked_cast")
    override fun apply(txArgs: Array<out Any?>): LedgerTransaction {
        return LedgerTransaction.createForSandbox(
            inputs = (txArgs[TX_INPUTS] as Array<Array<out Any?>>).map { it.toStateAndRef() },
            outputs = (txArgs[TX_OUTPUTS] as? List<TransactionState<ContractState>>) ?: emptyList(),
            commands = (txArgs[TX_COMMANDS] as? List<CommandWithParties<CommandData>>) ?: emptyList(),
            attachments = (txArgs[TX_ATTACHMENTS] as? List<Attachment>) ?: emptyList(),
            id = txArgs[TX_ID] as SecureHash,
            notary = txArgs[TX_NOTARY] as? Party,
            timeWindow = txArgs[TX_TIME_WINDOW] as? TimeWindow,
            privacySalt = txArgs[TX_PRIVACY_SALT] as PrivacySalt,
            networkParameters = txArgs[TX_NETWORK_PARAMETERS] as NetworkParameters,
            references = (txArgs[TX_REFERENCES] as Array<Array<out Any?>>).map { it.toStateAndRef() },
            digestService = if (txArgs.size > TX_DIGEST_SERVICE) (txArgs[TX_DIGEST_SERVICE] as DigestService) else DigestService.sha2_256
        )
    }

    private fun Array<*>.toStateAndRef(): StateAndRef<ContractState> {
        return StateAndRef(this[0] as TransactionState<*>, this[1] as StateRef)
    }
}
