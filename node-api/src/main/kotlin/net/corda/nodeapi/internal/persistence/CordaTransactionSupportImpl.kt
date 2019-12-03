package net.corda.nodeapi.internal.persistence

import net.corda.core.node.services.vault.CordaTransactionSupport
import net.corda.core.node.services.vault.SessionScope

/**
 * Helper class that wraps [CordaPersistence] and limits operations on it down to methods exposed by [CordaTransactionSupport].
 */
class CordaTransactionSupportImpl(private val persistence: CordaPersistence) : CordaTransactionSupport {
    override fun <T> transaction(statement: SessionScope.() -> T): T {
        fun DatabaseTransaction.innerFunc(): T {
            return statement.invoke(
                object : SessionScope {
                    override val session = this@innerFunc.session
                })
        }
        return persistence.transaction(DatabaseTransaction::innerFunc)
    }
}