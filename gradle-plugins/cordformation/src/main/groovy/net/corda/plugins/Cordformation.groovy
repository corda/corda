package net.corda.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * The Cordformation plugin deploys nodes to a directory in a state ready to be used by a developer for experimentation,
 * testing, and debugging. It will prepopulate several fields in the configuration and create a simple node runner.
 */
class Cordformation implements Plugin<Project> {
    /**
     * Gets a resource file from this plugin's JAR file.
     *
     * @param project The project environment this plugin executes in.
     * @param filePathInJar The file in the JAR, relative to root, you wish to access.
     * @return A file handle to the file in the JAR.
     */
    protected static File getPluginFile(Project project, String filePathInJar) {
        return project.rootProject.resources.text.fromArchiveEntry(project.rootProject.buildscript.configurations.classpath.find {
            it.name.contains('cordformation')
        }, filePathInJar).asFile()
    }

    void apply(Project project) {
        Utils.createCompileConfiguration("cordapp", project)
    }
}
