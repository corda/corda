package net.corda.behave.scenarios.tests

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.StepsContainer
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.api.StepsProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StepsProviderTests {

    @Test
    fun `module can discover steps providers`() {

        val foundProviders = StepsContainer.Companion.stepsProviders
        assertThat(foundProviders).hasOnlyElementsOfType(FooStepsProvider::class.java).hasSize(1)
    }

    class FooStepsProvider : StepsProvider {

        override val name: String
            get() = "Foo"

        override val stepsDefinition: StepsBlock
            get() = DummyStepsBlock()
    }

    class DummyStepsBlock : StepsBlock {
        override fun initialize(state: ScenarioState) {
        }
    }
}