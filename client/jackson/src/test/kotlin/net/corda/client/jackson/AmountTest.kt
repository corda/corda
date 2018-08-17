package net.corda.client.jackson

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.client.jackson.internal.CordaModule
import net.corda.core.contracts.Amount
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*

class AmountTest {
    private companion object {
        private val CO2 = CarbonCredit("CO2")
        private val jsonMapper: ObjectMapper = ObjectMapper().registerModule(CordaModule())
        private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerModule(CordaModule())
    }

    @Test
    fun `Amount(Currency) JSON deserialization`() {
        val str = """{ "quantity": 100, "token": "USD" }"""
        val amount = jsonMapper.readValue<Amount<Currency>>(str, object : TypeReference<Amount<Currency>>() {})
        assertThat(amount.quantity).isEqualTo(100)
        assertThat(amount.token).isEqualTo(Currency.getInstance("USD"))
    }

    @Test
    fun `Amount(Currency) YAML deserialization`() {
        val str = """{ quantity: 100, token: USD }"""
        val amount = yamlMapper.readValue<Amount<Currency>>(str, object : TypeReference<Amount<Currency>>() {})
        assertThat(amount.quantity).isEqualTo(100)
        assertThat(amount.token).isEqualTo(Currency.getInstance("USD"))
    }

    @Test
    fun `Amount(CarbonCredit) JSON deserialization`() {
        val str = """{ "quantity": 200, "token": { "type": "CO2" } }"""
        val amount = jsonMapper.readValue<Amount<CarbonCredit>>(str, object : TypeReference<Amount<CarbonCredit>>() {})
        assertThat(amount.quantity).isEqualTo(200)
        assertThat(amount.token).isEqualTo(CO2)
    }

    @Test
    fun `Amount(CarbonCredit) YAML deserialization`() {
        val str = """{ quantity: 250, token: { type: CO2 } }"""
        val amount = yamlMapper.readValue<Amount<CarbonCredit>>(str, object : TypeReference<Amount<CarbonCredit>>() {})
        assertThat(amount.quantity).isEqualTo(250)
        assertThat(amount.token).isEqualTo(CO2)
    }

    @Test
    fun `Amount(Unknown) JSON deserialization`() {
        val str = """{ "quantity": 100, "token": "USD" }"""
        val amount = jsonMapper.readValue<Amount<*>>(str, object : TypeReference<Amount<*>>() {})
        assertThat(amount.quantity).isEqualTo(100)
        assertThat(amount.token).isEqualTo(Currency.getInstance("USD"))
    }

    @Test
    fun `Amount(Unknown) YAML deserialization`() {
        val str = """{ quantity: 100, token: USD }"""
        val amount = yamlMapper.readValue<Amount<*>>(str, object : TypeReference<Amount<*>>() {})
        assertThat(amount.quantity).isEqualTo(100)
        assertThat(amount.token).isEqualTo(Currency.getInstance("USD"))
    }

    @Test
    fun `Amount(Currency) YAML serialization`() {
        assertThat(yamlMapper.valueToTree<TextNode>(Amount.parseCurrency("£25000000"))).isEqualTo(TextNode("25000000.00 GBP"))
        assertThat(yamlMapper.valueToTree<TextNode>(Amount.parseCurrency("$250000"))).isEqualTo(TextNode("250000.00 USD"))
    }

    @Test
    fun `Amount(CarbonCredit) JSON serialization`() {
        assertThat(jsonMapper.writeValueAsString(Amount(123456, CO2)).trim())
                .isEqualTo(""""123456 CarbonCredit(type=CO2)"""")
    }

    @Test
    fun `Amount(CarbonCredit) YAML serialization`() {
        assertThat(yamlMapper.writeValueAsString(Amount(123456, CO2)).trim())
                .isEqualTo("""--- "123456 CarbonCredit(type=CO2)"""")
    }

    data class CarbonCredit(@JsonProperty("type") val type: String)
}