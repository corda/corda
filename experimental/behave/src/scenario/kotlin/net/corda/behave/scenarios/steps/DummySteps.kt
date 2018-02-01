package net.corda.behave.scenarios.steps

import net.corda.behave.scenarios.StepsBlock
import org.assertj.core.api.Assertions.assertThat

fun dummySteps(steps: StepsBlock) = steps {

    When<Int, String>("^(\\d+) dumm(y|ies) exists?$") { count, _ ->
        state.count = count
        log.info("Checking pre-condition $count")
    }

    Then("^there is a dummy$") {
        assertThat(state.count).isGreaterThan(0)
        log.info("Checking outcome ${state.count}")
    }

}
