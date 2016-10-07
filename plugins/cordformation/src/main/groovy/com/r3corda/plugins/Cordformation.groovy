package com.r3corda.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.resources.TextResource

class Cordformation implements Plugin<Project> {
    void apply(Project project) {

    }

    static def getPluginFile(Project project, String filePathInJar) {
        return project.resources.text.fromArchiveEntry(project.buildscript.configurations.classpath.find {
            it.name.contains('cordformation')
        }, filePathInJar).asFile()
    }
}
