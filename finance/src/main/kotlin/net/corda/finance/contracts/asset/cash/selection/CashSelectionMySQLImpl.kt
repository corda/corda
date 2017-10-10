package net.corda.finance.contracts.asset.cash.selection

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Statement
import java.util.*

class CashSelectionMySQLImpl : CashSelection() {

    companion object {
        const val JDBC_DRIVER_NAME = "MySQL JDBC Driver"
    }

    override fun isCompatible(metadata: DatabaseMetaData): Boolean {
        return metadata.driverName == JDBC_DRIVER_NAME
    }

    override fun executeQuery(statement: Statement, amount: Amount<Currency>, lockId: UUID, notary: Party?, issuerKeysStr: String?, issuerRefsStr: String?): ResultSet {
        TODO("MySQL cash selection not implemented")
    }

    override fun toString() = "${this::class.java} for ${CashSelectionH2Impl.JDBC_DRIVER_NAME}"
}