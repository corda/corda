package net.corda.finance.contracts.asset.cash.selection

import net.corda.core.contracts.Amount
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.util.*

class CashSelectionMSSQLImpl : AbstractCashSelection() {
    companion object {
        const val JDBC_DRIVER_NAME = """Microsoft JDBC Driver (\w+.\w+) for SQL Server"""
        private val log = contextLogger()
    }

    override fun isCompatible(metadata: DatabaseMetaData): Boolean {
        return  JDBC_DRIVER_NAME.toRegex().matches(metadata.driverName)
//        return metadata.driverName == JDBC_DRIVER_NAME
    }

    override fun toString() = "${this::class.qualifiedName} for '$JDBC_DRIVER_NAME'"

    /**
     * This is one MSSQL implementation of the query to select just enough cash to meet the desired amount.
     * We select the cash states with smaller amounts first so that as the result, we minimize the numbers of
     * unspent cash states in the vault.
     * Common Table Expression and Windowed functions help make the query more readable.
     * Query plan does index scan on pennies_idx, which may be unavoidable due to the nature of the query.
     */
    override fun executeQuery(connection: Connection, amount: Amount<Currency>, lockId: UUID, notary: Party?, onlyFromIssuerParties: Set<AbstractParty>, withIssuerRefs: Set<OpaqueBytes>, withResultSet: (ResultSet) -> Boolean): Boolean {

        val selectJoin = """
            ;WITH CTE AS
            (
            SELECT
              vs.transaction_id,
              vs.output_index,
              ccs.pennies,
              vs.lock_id,
              total_pennies = SUM(ccs.pennies) OVER (ORDER BY ccs.pennies),
              seqNo = ROW_NUMBER() OVER (ORDER BY ccs.pennies)
            FROM vault_states AS vs INNER JOIN contract_cash_states AS ccs
                ON vs.transaction_id = ccs.transaction_id AND vs.output_index = ccs.output_index
            WHERE
              vs.state_status = 0
              AND ccs.ccy_code = ?
              AND (vs.lock_id = ? OR vs.lock_id IS NULL)
            """ +
                (if (notary != null)
                    " AND vs.notary_name = ?" else "") +
                (if (onlyFromIssuerParties.isNotEmpty())
                    " AND ccs.issuer_key_hash IN (?)" else "") +
                (if (withIssuerRefs.isNotEmpty())
                    " AND ccs.issuer_ref IN (?)" else "") +
                """
            ),
            Boundary AS
            (
              SELECT TOP (1) * FROM  CTE WHERE total_pennies >= ? ORDER BY seqNo
            )
            SELECT CTE.transaction_id, CTE.output_index, CTE.pennies, CTE.total_pennies, CTE.lock_id
              FROM CTE INNER JOIN Boundary AS B ON CTE.seqNo <= B.seqNo
            ;
            """
        connection.prepareStatement(selectJoin).use { psSelectJoin ->
            var pIndex = 0
            psSelectJoin.setString(++pIndex, amount.token.currencyCode)
            psSelectJoin.setString(++pIndex, lockId.toString())
            if (notary != null)
                psSelectJoin.setString(++pIndex, notary.name.toString())
            if (onlyFromIssuerParties.isNotEmpty())
                psSelectJoin.setObject(++pIndex, onlyFromIssuerParties.map { it.owningKey.toStringShort() as Any }.toTypedArray())
            if (withIssuerRefs.isNotEmpty())
                psSelectJoin.setObject(++pIndex, withIssuerRefs.map { it.bytes as Any }.toTypedArray())
            psSelectJoin.setLong(++pIndex, amount.quantity)

            log.debug { psSelectJoin.toString() }

            psSelectJoin.executeQuery().use { rs ->
                return withResultSet(rs)
            }
        }
    }
}