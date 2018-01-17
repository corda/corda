package net.corda.plugins

import org.gradle.api.Project

open class Cordapp private constructor(val coordinates: String?, val project: Project?) {
    constructor(coordinates: String) : this(coordinates, null)
    constructor(cordappProject: Project) : this(null, cordappProject)

    var config: String? = null

    fun config(config: String) {
        this.config = config
    }
}