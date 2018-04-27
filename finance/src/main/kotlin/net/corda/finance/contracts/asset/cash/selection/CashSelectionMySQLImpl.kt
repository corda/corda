package net.corda.finance.contracts.asset.cash.selection

import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.util.*

class CashSelectionMySQLImpl : AbstractCashSelection() {

    companion object {
        const val JDBC_DRIVER_NAME = "MySQL JDBC Driver"
    }

    override fun isCompatible(metadata: DatabaseMetaData): Boolean {
        return metadata.driverName == JDBC_DRIVER_NAME
    }

    override fun executeQuery(statement: Connection, amount: Amount<Currency>, lockId: UUID, notary: Party?, issuerKeysStr: Set<AbstractParty>, issuerRefsStr: Set<OpaqueBytes>, withResultSet: (ResultSet) -> Boolean): Boolean {
        TODO("MySQL cash selection not implemented")
    }

    override fun toString() = "${this::class.java} for '$JDBC_DRIVER_NAME'"
}