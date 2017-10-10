package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class Utils {
    companion object {
        @JvmStatic
        fun createCompileConfiguration(name: String, project: Project) {
            if(!project.configurations.any { it.name == name }) {
                val configuration = project.configurations.create(name)
                configuration.isTransitive = false
                project.configurations.single { it.name == "compile" }.extendsFrom(configuration)
            }
        }
    }
}