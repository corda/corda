package net.corda.nodeapi.internal.persistence

import net.corda.core.node.services.vault.CordaTransactionSupport
import net.corda.core.node.services.vault.SessionScope

/**
 * Helper class that wraps [CordaPersistence] and limits operations on it down to methods exposed by [CordaTransactionSupport].
 */
class CordaTransactionSupportImpl(private val persistence: CordaPersistence) : CordaTransactionSupport {
    override fun <T> transaction(statement: SessionScope.() -> T): T {
        // An alternative approach could be to make `DatabaseTransaction` extend from `SessionScope`, but this will introduce a hierarchical
        // dependency which might be unwanted in some cases.
        fun DatabaseTransaction.innerFunc(): T {
            return statement.invoke(
                object : SessionScope {
                    override val session = this@innerFunc.session
                })
        }
        return persistence.transaction(0, DatabaseTransaction::innerFunc)
    }
}