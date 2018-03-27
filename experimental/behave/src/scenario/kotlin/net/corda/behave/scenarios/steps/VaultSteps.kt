package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.helpers.Vault
import net.corda.core.contracts.ContractState
import net.corda.core.utilities.sumByLong
import net.corda.finance.contracts.asset.Cash

class VaultSteps(state: ScenarioState) : StepsBlock(state) {

    override fun initialize() {
        val vault = Vault(state)

        Then<String, Int>("^node (\\w+) vault contains (\\d+) states$") { node, count ->
            if (vault.query(node, ContractState::class.java).size == count)
                succeed()
            else
                fail("Vault on node $node does not contain expected number of states: $count")
        }

        Then<String, Int, String>("^node (\\w+) vault contains (\\d+) (\\w+) states$") { node, count, contractType ->
            try {
                val contractStateTypeClass = Class.forName(contractType) as Class<ContractState>
                if (vault.query(node, contractStateTypeClass).size == count)
                    succeed()
                else
                    fail("Vault on node $node does not contain expected number of states: $count")
            } catch (e: Exception) {
                fail("Invalid contract state class type: ${e.message}")
            }
        }

        Then<String, Long, String>("^node (\\w+) vault contains total cash of (\\d+) (\\w+)$") { node, total, currency ->
            val cashStates = vault.query(node, Cash.State::class.java)
            val sumCashStates = cashStates.filter { it.state.data.amount.token.product.currencyCode == currency }?.sumByLong { it.state.data.amount.quantity }
            print((sumCashStates))
            if (sumCashStates == total)
                succeed()
            else
                fail("Vault on node $node does not contain total cash of : $total")
        }
    }
}
