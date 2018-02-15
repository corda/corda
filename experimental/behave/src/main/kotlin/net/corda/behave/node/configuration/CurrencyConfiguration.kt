package net.corda.behave.node.configuration

class CurrencyConfiguration(private val issuableCurrencies: List<String>) : ConfigurationTemplate() {

    override val config: (Configuration) -> String
        get() = {
            if (issuableCurrencies.isEmpty()) {
                ""
            } else {
                """
                |issuableCurrencies=[
                |    ${issuableCurrencies.joinToString(", ")}
                |]
                """
            }
        }

}