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
        const val CORDFORMATION_TYPE = "cordformationInternal"

        /**
         * Gets a resource file from this plugin's JAR file.
         *
         * @param project The project environment this plugin executes in.
         * @param filePathInJar The file in the JAR, relative to root, you wish to access.
         * @return A file handle to the file in the JAR.
         */
        fun getPluginFile(project: Project, filePathInJar: String): File {
            val archive = project.rootProject.buildscript.configurations
                    .single { it.name == "classpath" }
                    .first { it.name.contains("cordformation") }
            return project.rootProject.resources.text
                    .fromArchiveEntry(archive, filePathInJar)
                    .asFile()
        }

        /**
         * Gets a current built corda jar file
         *
         * @param project The project environment this plugin executes in.
         * @param jarName The name of the JAR you wish to access.
         * @return A file handle to the file in the JAR.
         */
        fun verifyAndGetRuntimeJar(project: Project, jarName: String): File {
            val releaseVersion = project.rootProject.ext<String>("corda_release_version")
            val maybeJar = project.configuration("runtime").filter {
                "$jarName-$releaseVersion.jar" in it.toString() || "$jarName-enterprise-$releaseVersion.jar" in it.toString()
            }
            if (maybeJar.isEmpty) {
                throw IllegalStateException("No $jarName JAR found. Have you deployed the Corda project to Maven? Looked for \"$jarName-$releaseVersion.jar\"")
            } else {
                val jar = maybeJar.singleFile
                require(jar.isFile)
                return jar
            }
        }

        val executableFileMode = "0755".toInt(8)
    }

    override fun apply(project: Project) {
        Utils.createCompileConfiguration("cordapp", project)
        Utils.createRuntimeConfiguration(CORDFORMATION_TYPE, project)
        // TODO: improve how we re-use existing declared external variables from root gradle.build
        val jolokiaVersion = try { project.rootProject.ext<String>("jolokia_version") } catch (e: Exception) { "1.6.0" }
        project.dependencies.add(CORDFORMATION_TYPE, "org.jolokia:jolokia-jvm:$jolokiaVersion:agent")
    }
}
