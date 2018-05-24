package net.corda.sandbox.utilities

import net.corda.sandbox.TestBase
import net.corda.sandbox.code.DefinitionProvider
import net.corda.sandbox.code.Emitter
import net.corda.sandbox.rules.Rule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DiscoveryTest : TestBase() {

    @Test
    fun `can discover rules automatically`() {
        val rules = ALL_RULES
        assertThat(rules.size).isGreaterThan(0)
        assertThat(rules).allMatch { it is Rule }
    }

    @Test
    fun `can discover definition providers automatically`() {
        val definitionProviders = ALL_DEFINITION_PROVIDERS
        assertThat(definitionProviders.size).isGreaterThan(0)
        assertThat(definitionProviders).allMatch { it is DefinitionProvider }
    }

    @Test
    fun `can discover emitters automatically`() {
        val emitters = ALL_EMITTERS
        assertThat(emitters.size).isGreaterThan(0)
        assertThat(emitters).allMatch { it is Emitter }
    }

}
