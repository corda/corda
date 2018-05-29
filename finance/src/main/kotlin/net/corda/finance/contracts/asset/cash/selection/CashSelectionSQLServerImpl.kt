/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts.asset.cash.selection

import net.corda.core.contracts.Amount
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.toBase58String
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.util.*

/**
 * SQL Server / SQL Azure
 */
class CashSelectionSQLServerImpl : AbstractCashSelection(maxRetries = 16, retrySleep = 1000, retryCap = 5000) {

    companion object {
        const val JDBC_DRIVER_NAME = "Microsoft JDBC Driver"
        private val log = contextLogger()
    }

    override fun isCompatible(metaData: DatabaseMetaData): Boolean {
        return metaData.driverName.startsWith(JDBC_DRIVER_NAME, ignoreCase = true)
    }

    override fun toString() = "${this::class.qualifiedName} for '$JDBC_DRIVER_NAME'"

    override fun executeQuery(connection: Connection, amount: Amount<Currency>, lockId: UUID, notary: Party?,
                              onlyFromIssuerParties: Set<AbstractParty>,
                              withIssuerRefs: Set<OpaqueBytes>, withResultSet: (ResultSet) -> Boolean): Boolean {

        val selectJoin = """
            WITH row(transaction_id, output_index, pennies, total, lock_id) AS
            (
            SELECT vs.transaction_id, vs.output_index, ccs.pennies,
            SUM(ccs.pennies) OVER (ORDER BY ccs.transaction_id, ccs.output_index RANGE UNBOUNDED PRECEDING), vs.lock_id
            FROM contract_cash_states AS ccs, vault_states AS vs
            WHERE vs.transaction_id = ccs.transaction_id AND vs.output_index = ccs.output_index
                AND vs.state_status = 0
                AND ccs.ccy_code = ?
                AND (vs.lock_id = ? OR vs.lock_id is null)"""+
                (if (notary != null)
                    " AND vs.notary_name = ?" else "") +
                // mssql-server driver does not implement setArray(), so in the following way
                // we explicitly unpack the parameters list
                (if (onlyFromIssuerParties.isNotEmpty()) {
                    val repeats = generateSequence { "?" }
                            .take(onlyFromIssuerParties.size)
                            .joinToString (",")
                    " AND ccs.issuer_key_hash IN ($repeats)"
                    } else { "" }) +
                (if (withIssuerRefs.isNotEmpty()) {
                    val repeats = generateSequence { "?" }
                            .take(withIssuerRefs.size)
                            .joinToString (",")
                    " AND ccs.issuer_ref IN ($repeats)"
                } else { "" }) +
                """)
            SELECT row.transaction_id, row.output_index, row.pennies, row.total, row.lock_id
            FROM row where row.total <= ? + row.pennies"""

        // Use prepared statement for protection against SQL Injection
        connection.prepareStatement(selectJoin).use { statement ->
            var pIndex = 0
            statement.setString(++pIndex, amount.token.currencyCode)
            statement.setString(++pIndex, lockId.toString())
            if (notary != null)
                statement.setString(++pIndex, notary.name.toString())
            onlyFromIssuerParties.map { it.owningKey.toStringShort() }.forEach {
                statement.setObject(++pIndex, it)
            }
            withIssuerRefs.map { it.bytes }.forEach {
                statement.setBytes(++pIndex, it)
            }
            statement.setLong(++pIndex, amount.quantity)
            log.debug(selectJoin)

            statement.executeQuery().use { rs ->
                return withResultSet(rs)
            }
        }
    }
}