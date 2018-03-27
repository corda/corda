package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.helpers.Cash
import org.assertj.core.api.Assertions.assertThat

class CashSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        val cash = Cash(state)

        Then<String>("^node (\\w+) has 1 issuable currency$") { name ->
            state.withNetwork {
                assertThat(cash.numberOfIssuableCurrencies(name)).isEqualTo(1)
            }
        }

        Then<String, String>("^node (\\w+) has (\\w+) issuable currencies$") { name, count ->
            state.withNetwork {
                assertThat(cash.numberOfIssuableCurrencies(name)).isEqualTo(count.toInt())
            }
        }

        Then<String, Long, String, String>("^node (\\w+) can transfer (\\d+) (\\w+) to node (\\w+)$") { nodeA, amount, currency, nodeB ->
            state.withNetwork {
                cash.transferCash(nodeA, nodeB, amount, currency)
            }
        }

        Then<String, Long, String>("^node (\\w+) can issue (\\d+) (\\w+)$") { nodeA, amount, currency ->
            state.withNetwork {
                cash.issueCash(nodeA, amount, currency)
            }
        }
    }
}
