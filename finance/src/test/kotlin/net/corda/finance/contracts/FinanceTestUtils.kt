@file:JvmName("FinanceTestUtils")

package net.corda.finance.contracts

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.PartyAndAmount
import net.corda.testing.chooseIdentityAndCert
import java.security.PublicKey
import java.util.*

/**
 * Generate a transaction that moves money of the given amounts to the recipients specified. This uses the first
 * identity in the service hub as our identity for the purposes of generating a confidential identity to send change
 * to.
 */
@Throws(InsufficientBalanceException::class)
@Suspendable
fun generateSpend(services: ServiceHub,
                  tx: TransactionBuilder,
                  payments: List<PartyAndAmount<Currency>>): Pair<TransactionBuilder, List<PublicKey>> {
    return Cash.generateSpend(services, tx, payments, services.myInfo.chooseIdentityAndCert())
}

/**
 * Generate a transaction that moves an amount of currency to the given party. This uses the first
 * identity in the service hub as our identity for the purposes of generating a confidential identity to send change
 * to.
 */
fun generateSpend(services: ServiceHub,
                  tx: TransactionBuilder,
                  amount: Amount<Currency>,
                  to: AbstractParty,
                  onlyFromParties: Set<AbstractParty> = emptySet()): Pair<TransactionBuilder, List<PublicKey>> {
    return Cash.generateSpend(services, tx, amount, services.myInfo.chooseIdentityAndCert(), to, onlyFromParties)
}
