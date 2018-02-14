package net.corda.cordform

data class CordappDependency(
    val mavenCoordinates: String? = null,
    val projectName: String? = null
) {
    init {
        require((mavenCoordinates != null) != (projectName != null), { "Only one of maven coordinates or project name must be set" })
    }
}