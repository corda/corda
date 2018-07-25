package net.corda.testing

import net.corda.nodeapi.internal.persistence.CordaPersistence
import java.util.*

/**
 * Cleans all the vault + transaction tables so we can re-use the same database for multiple tests.
 */
fun cleanDatabase(database: CordaPersistence) {
    database.transaction {
        val session = database.createSession()
        val statement = session.createStatement()

        statement.execute("SET REFERENTIAL_INTEGRITY FALSE")

        val availableTables = HashSet<String>()

        val repeats = generateSequence { "?" }.take(tablesToNuke.size).joinToString(",")
        val sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES AS TABLES WHERE TABLES.TABLE_NAME IN ($repeats)"
        session.prepareStatement(sql).use { preparedStatement ->
            var offset = 0
            tablesToNuke.forEach { preparedStatement.setString(++offset, it) }
            val results = preparedStatement.executeQuery()
            while (results.next()) {
                availableTables.add(results.getString(1))
            }
            results.close()
        }

        availableTables.forEach {
            statement.executeUpdate("TRUNCATE TABLE $it;")
        }

        statement.execute("SET REFERENTIAL_INTEGRITY TRUE")
    }
}

val tablesToNuke = setOf(
        "NODE_SCHEDULED_STATES",
        "NODE_NAMED_IDENTITIES",
        "NODE_CONTRACT_UPGRADES",
        "NODE_MESSAGE_IDS",
        "VAULT_TRANSACTION_NOTES",
        "NODE_CHECKPOINTS",
        "VAULT_STATES",
        "VAULT_LINEAR_STATES",
        "VAULT_FUNGIBLE_STATES_PARTS",
        "VAULT_LINEAR_STATES_PARTS",
        "NODE_TRANSACTION_MAPPINGS",
        "CONTRACT_CASH_STATES",
        "VAULT_FUNGIBLE_STATES",
        "NODE_TRANSACTIONS",
        "cash_state_participants",
        "cash_states_v2_participants",
        "cp_states_v2_participants",
        "dummy_linear_state_parts",
        "dummy_linear_states_v2_parts",
        "dummy_deal_states_parts",
        "vault_fungible_states_parts",
        "vault_linear_states_parts",
        "vault_fungible_states",
        "cash_states_v2",
        "cash_states_v3",
        "cp_states_v1",
        "cp_states_v2",
        "state_participants",
        "dummy_deal_states",
        "dummy_linear_states",
        "cp_states",
        "contract_cash_states",
        "contract_cash_states_v"
)