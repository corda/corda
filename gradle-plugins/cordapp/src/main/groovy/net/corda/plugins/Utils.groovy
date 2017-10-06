package net.corda.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class Utils {
    public static void createCompileConfiguration(String name, Project project) {
        if(project.configurations.any { it.name == name }) {
            Configuration configuration = project.configurations.create(name)
            configuration.transitive = false
            project.configurations.compile.extendsFrom configuration
        }
    }
}