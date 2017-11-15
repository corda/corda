package net.corda.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * The Cordformation plugin deploys nodes to a directory in a state ready to be used by a developer for experimentation,
 * testing, and debugging. It will prepopulate several fields in the configuration and create a simple node runner.
 */
class Cordformation : Plugin<Project> {
    internal companion object {
        /**
         * Gets a resource file from this plugin's JAR file.
         *
         * @param project The project environment this plugin executes in.
         * @param filePathInJar The file in the JAR, relative to root, you wish to access.
         * @return A file handle to the file in the JAR.
         */
        fun getPluginFile(project: Project, filePathInJar: String): File {
            val archive: File? = project.rootProject.buildscript.configurations
                    .single { it.name == "classpath" }
                    .find { it.name.contains("cordformation") }
            return project.rootProject.resources.text
                    .fromArchiveEntry(archive, filePathInJar)
                    .asFile()
        }

        val executableFileMode = "0755".toInt(8)
    }

    override fun apply(project: Project) {
        Utils.createCompileConfiguration("cordapp", project)
        // TODO enterprise, group
        if(!project.rootProject.name.equals("corda-project")) {
            project.dependencies.add("runtime", "net.corda:corda-node:${project.rootProject.ext<String>("cordaVersion")}")
        }
    }
}
