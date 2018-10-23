package net.corda.behave.node.configuration

// TODO This is a ConfigurationTemplate but is never used as one. Therefore the private "applications" list is never used
// and thus includeFinance isn't necessary either. Something is amiss.
class CordappConfiguration(var apps: List<String> = emptyList(), val includeFinance: Boolean = false) : ConfigurationTemplate() {

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
