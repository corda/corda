package net.corda.plugins

class ProjectPublishExtension {
    /**
     * Use a different name from the current project name for publishing
     */
    String name
    /**
     * True when we do not want to publish default Java components
     */
    Boolean disableDefaultJar = false
}