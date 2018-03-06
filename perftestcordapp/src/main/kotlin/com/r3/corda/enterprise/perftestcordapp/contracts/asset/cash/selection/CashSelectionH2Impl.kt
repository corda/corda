/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.enterprise.perftestcordapp.contracts.asset.cash.selection

import net.corda.core.contracts.Amount
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.utilities.*
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.util.*

class CashSelectionH2Impl : AbstractCashSelection() {

    companion object {
        const val JDBC_DRIVER_NAME = "H2 JDBC Driver"
        private val log = contextLogger()
    }

    override fun isCompatible(metadata: DatabaseMetaData): Boolean {
        return metadata.driverName == JDBC_DRIVER_NAME
    }

    override fun toString() = "${this::class.java} for $JDBC_DRIVER_NAME"

    //       We are using an H2 specific means of selecting a minimum set of rows that match a request amount of coins:
    //       1) There is no standard SQL mechanism of calculating a cumulative total on a field and restricting row selection on the
    //          running total of such an accumulator
    //       2) H2 uses session variables to perform this accumulator function:
    //          http://www.h2database.com/html/functions.html#set
    //       3) H2 does not support JOIN's in FOR UPDATE (hence we are forced to execute 2 queries)
    override fun executeQuery(connection: Connection, amount: Amount<Currency>, lockId: UUID, notary: Party?, onlyFromIssuerParties: Set<AbstractParty>, withIssuerRefs: Set<OpaqueBytes>, withResultSet: (ResultSet) -> Boolean): Boolean {
        connection.createStatement().use { it.execute("CALL SET(@t, CAST(0 AS BIGINT));") }

        val selectJoin = """
                    SELECT vs.transaction_id, vs.output_index, ccs.pennies, SET(@t, ifnull(@t,0)+ccs.pennies) total_pennies, vs.lock_id
                    FROM vault_states AS vs, contract_pt_cash_states AS ccs
                    WHERE vs.transaction_id = ccs.transaction_id AND vs.output_index = ccs.output_index
                    AND vs.state_status = 0
                    AND ccs.ccy_code = ? and @t < ?
                    AND (vs.lock_id = ? OR vs.lock_id is null)
                    """ +
                (if (notary != null)
                    " AND vs.notary_name = ?" else "") +
                (if (onlyFromIssuerParties.isNotEmpty())
                    " AND ccs.issuer_key_hash IN (?)" else "") +
                (if (withIssuerRefs.isNotEmpty())
                    " AND ccs.issuer_ref IN (?)" else "")

        // Use prepared statement for protection against SQL Injection (http://www.h2database.com/html/advanced.html#sql_injection)
        connection.prepareStatement(selectJoin).use { psSelectJoin ->
            var pIndex = 0
            psSelectJoin.setString(++pIndex, amount.token.currencyCode)
            psSelectJoin.setLong(++pIndex, amount.quantity)
            psSelectJoin.setString(++pIndex, lockId.toString())
            if (notary != null)
                psSelectJoin.setString(++pIndex, notary.name.toString())
            if (onlyFromIssuerParties.isNotEmpty())
                psSelectJoin.setObject(++pIndex, onlyFromIssuerParties.map { it.owningKey.toStringShort() as Any}.toTypedArray() )
            if (withIssuerRefs.isNotEmpty())
                psSelectJoin.setObject(++pIndex, withIssuerRefs.map { it.bytes.toHexString() as Any }.toTypedArray())
            log.debug { psSelectJoin.toString() }

            psSelectJoin.executeQuery().use { rs ->
                return withResultSet(rs)
            }
        }
    }
}