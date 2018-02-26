package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock
import org.assertj.core.api.Assertions.assertThat

fun cashSteps(steps: StepsBlock) = steps {

    Then<String>("^node (\\w+) has 1 issuable currency$") { name ->
        withNetwork {
            assertThat(cash.numberOfIssuableCurrencies(name)).isEqualTo(1)
        }
    }

    Then<String, String>("^node (\\w+) has (\\w+) issuable currencies$") { name, count ->
        withNetwork {
            assertThat(cash.numberOfIssuableCurrencies(name)).isEqualTo(count.toInt())
        }
    }

    Then<String,Long,String,String>("^node (\\w+) can transfer (\\d+) (\\w+) to node (\\w+)$") { nodeA, amount, currency, nodeB ->
        withNetwork {
            cash.transferCash(nodeA, nodeB, amount, currency)
        }
    }

    Then<String,Long,String>("^node (\\w+) can issue (\\d+) (\\w+)$") { nodeA, amount, currency ->
        withNetwork {
            cash.issueCash(nodeA, amount, currency)
        }
    }
}
