package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock
import net.corda.core.contracts.ContractState
import net.corda.finance.contracts.asset.Cash

fun vaultSteps(steps: StepsBlock) = steps {

    Then<String,Int>("^node (\\w+) vault contains (\\d+) states$") { node, count ->
        if (vault.query(node, ContractState::class.java).size == count)
            succeed()
        else
            fail("Vault on node $node does not contain expected number of states: $count")
    }

    Then<String,Int,String>("^node (\\w+) vault contains (\\d+) (\\w+) states$") { node, count, contractType ->
        try {
            val contractStateTypeClass = Class.forName(contractType) as Class<ContractState>
            if (vault.query(node, contractStateTypeClass).size == count)
                succeed()
            else
                fail("Vault on node $node does not contain expected number of states: $count")
        }
        catch (e: Exception) {
            fail("Invalid contract state class type: ${e.message}")
        }
    }

    Then<String,Long,String>("^node (\\w+) vault contains total cash of (\\d+) (\\w+)$") { node, total, currency ->
        val cashStates = vault.query(node, Cash.State::class.java)
        val sumCashStates = cashStates.filter { it.state.data.amount.token.product.currencyCode == currency }?.sumBy { it.state.data.amount.quantity.toInt() }
        print((sumCashStates))
//        if (sumCashStates!! == total)
//            succeed()
//        else
            fail("Vault on node $node does not contain total cash of : $total")
    }
}
