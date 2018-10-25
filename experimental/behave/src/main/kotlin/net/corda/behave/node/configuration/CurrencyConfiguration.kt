package net.corda.behave.node.configuration

class CurrencyConfiguration(private val issuableCurrencies: List<String>) : ConfigurationTemplate() {

    override val config: (Configuration) -> String
        get() = {
            if (issuableCurrencies.isEmpty()) {
                ""
            } else {
                // TODO This is no longer correct. issuableCurrencies is a config of the finance app and belongs
                // in a separate .conf file for the app (in the config sub-directory, with a filename matching the CorDapp
                // jar filename). It is no longer read in from the node conf file. There seem to be pieces missing in the
                // behave framework to allow one to do this.
                """
                |custom : {
                |   issuableCurrencies : [
                |       ${issuableCurrencies.joinToString(", ")}
                |   ]
                |}
                """
            }
        }
}