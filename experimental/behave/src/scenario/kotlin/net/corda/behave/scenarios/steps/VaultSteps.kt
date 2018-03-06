package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock
import net.corda.core.contracts.ContractState

fun vaultSteps(steps: StepsBlock) = steps {

    Then<String,Int>("^node (\\w+) vault contains (\\d+) trade$") { node, count ->
        if (vault.query(node, ContractState::class.java) == count)
            succeed()
        else
            fail("Vault on node $node does not contain expected number of states: $count")
    }
}
