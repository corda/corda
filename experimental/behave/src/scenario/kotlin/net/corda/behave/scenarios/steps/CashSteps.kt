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

}
