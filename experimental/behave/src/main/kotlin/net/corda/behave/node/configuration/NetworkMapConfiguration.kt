package net.corda.behave.node.configuration

class NetworkMapConfiguration(private val compatibilityZoneURL: String? = null) : ConfigurationTemplate() {

    override val config: (Configuration) -> String
        get() = {
            if (compatibilityZoneURL != null) {
                """
                |compatibilityZoneURL="$compatibilityZoneURL"
                """
            } else {
                ""
            }
        }
}