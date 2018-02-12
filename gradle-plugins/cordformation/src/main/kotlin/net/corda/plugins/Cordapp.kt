package net.corda.plugins

import org.gradle.api.Project
import java.io.File

open class Cordapp private constructor(val coordinates: String?, val project: Project?) {
    constructor(coordinates: String) : this(coordinates, null)
    constructor(cordappProject: Project) : this(null, cordappProject)

    // The configuration text that will be written
    internal var config: String? = null

    /**
     * Set the configuration text that will be written to the cordapp's configuration file
     */
    fun config(config: String) {
        this.config = config
    }

    /**
     * Reads config from the file and later writes it to the cordapp's configuration file
     */
    fun config(configFile: File) {
        this.config = configFile.readText()
    }
}