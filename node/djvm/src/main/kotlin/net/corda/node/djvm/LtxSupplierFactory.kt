@file:JvmName("LtxTools")
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
import java.util.function.Supplier

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

class LtxSupplierFactory : Function<Array<out Any?>, Supplier<LedgerTransaction>> {
    @Suppress("unchecked_cast")
    override fun apply(txArgs: Array<out Any?>): Supplier<LedgerTransaction> {
        val inputProvider = (txArgs[TX_INPUTS] as Function<in Any?, Array<Array<out Any?>>>)
                .andThen(Function(Array<Array<out Any?>>::toContractStatesAndRef))
                .toSupplier()
        val outputProvider = txArgs[TX_OUTPUTS] as? Supplier<List<TransactionState<ContractState>>> ?: Supplier(::emptyList)
        val commandsProvider = txArgs[TX_COMMANDS] as Supplier<List<CommandWithParties<CommandData>>>
        val referencesProvider = (txArgs[TX_REFERENCES] as Function<in Any?, Array<Array<out Any?>>>)
                .andThen(Function(Array<Array<out Any?>>::toContractStatesAndRef))
                .toSupplier()
        val networkParameters = (txArgs[TX_NETWORK_PARAMETERS] as? NetworkParameters)?.toImmutable()
        return Supplier {
            LedgerTransaction.createForContractVerify(
                inputs = inputProvider.get(),
                outputs = outputProvider.get(),
                commands = commandsProvider.get(),
                attachments = txArgs[TX_ATTACHMENTS] as? List<Attachment> ?: emptyList(),
                id = txArgs[TX_ID] as SecureHash,
                notary = txArgs[TX_NOTARY] as? Party,
                timeWindow = txArgs[TX_TIME_WINDOW] as? TimeWindow,
                privacySalt = txArgs[TX_PRIVACY_SALT] as PrivacySalt,
                networkParameters = networkParameters,
                references = referencesProvider.get(),
                digestService = txArgs[TX_DIGEST_SERVICE] as DigestService
            )
        }
    }
}

private fun <T> Function<in Any?, T>.toSupplier(): Supplier<T> {
    return Supplier { apply(null) }
}

private fun Array<Array<out Any?>>.toContractStatesAndRef(): List<StateAndRef<ContractState>> {
    return map(Array<out Any?>::toStateAndRef)
}

private fun Array<*>.toStateAndRef(): StateAndRef<ContractState> {
    return StateAndRef(this[0] as TransactionState<*>, this[1] as StateRef)
}
