package net.corda.contracts.djvm.security

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.security.AccessController.doPrivileged
import java.security.PrivilegedAction

class DeterministicSecureContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        /**
         * Our [SecurityManager] should prevent this class from
         * closing its own [ClassLoader]. Nor should using
         * [doPrivileged] change this, because this contract
         * should be running without any privileges of its own.
         */
        doPrivileged(PrivilegedAction {
            (this::class.java.classLoader as AutoCloseable).close()
        })
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class State(val owner: AbstractParty, val secureData: SecureHash) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return secureData.toString()
        }
    }

    class Operate : CommandData
}