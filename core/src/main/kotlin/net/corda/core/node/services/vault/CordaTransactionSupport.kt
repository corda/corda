package net.corda.core.node.services.vault

import net.corda.core.DoNotImplement

@DoNotImplement
interface CordaTransactionSupport {
    /**
     * Executes given statement in the scope of transaction with the transaction level specified at the creation time.
     * @param statement to be executed in the scope of this transaction.
     */
    fun <T> transaction(statement: VaultTransaction.() -> T): T
}