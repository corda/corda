package net.corda.contracts.asset.cash.selection

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.CashSelection
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.OpaqueBytes
import java.sql.DatabaseMetaData
import java.util.*

class CashSelectionMySQLImpl : CashSelection {

    companion object {
        val JDBC_DRIVER_NAME = "MySQL JDBC Driver"
    }

    override fun isCompatible(metadata: DatabaseMetaData): Boolean {
        return metadata.driverName == JDBC_DRIVER_NAME
    }

    override fun unconsumedCashStatesForSpending(services: ServiceHub,
                                                 amount: Amount<Currency>,
                                                 onlyFromIssuerParties: Set<AbstractParty>,
                                                 notary: Party?,
                                                 lockId: UUID,
                                                 withIssuerRefs: Set<OpaqueBytes>): List<StateAndRef<Cash.State>> {
        TODO("MySQL cash selection not implemented")
    }
 }