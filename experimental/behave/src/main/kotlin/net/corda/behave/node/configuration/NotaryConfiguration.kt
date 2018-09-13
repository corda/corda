package net.corda.behave.node.configuration

class NotaryConfiguration(private val notaryType: NotaryType = NotaryType.NONE) : ConfigurationTemplate() {

    override val config: (Configuration) -> String
        get() = {
            when (notaryType) {
                NotaryType.NONE -> ""
                NotaryType.NON_VALIDATING ->
                    "notary { validating = false }"
                NotaryType.VALIDATING ->
                    "notary { validating = true }"
            }
        }
}
