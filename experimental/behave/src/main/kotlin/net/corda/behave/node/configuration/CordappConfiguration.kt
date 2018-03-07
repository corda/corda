package net.corda.behave.node.configuration

class CordappConfiguration(var apps: List<String> = emptyList(), var includeFinance: Boolean = false) : ConfigurationTemplate() {

    private val applications = apps + if (includeFinance) {
        listOf("net.corda:corda-finance:CORDA_VERSION")
    } else {
        emptyList()
    }

    override val config: (Configuration) -> String
        get() = { config ->
            if (applications.isEmpty()) {
                ""
            } else {
                """
                |cordapps = [
                |${applications.joinToString(", ") { formatApp(config, it) }}
                |]
                """
            }
        }

    private fun formatApp(config: Configuration, app: String): String {
        return "\"${app.replace("CORDA_VERSION", config.distribution.version)}\""
    }

}
