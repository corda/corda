package net.corda.plugins

import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Creates nodes based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Cordform : Baseform() {
    private companion object {
        val nodeJarName = "corda.jar"
        private val defaultDirectory: Path = Paths.get("build", "nodes")
    }

    /**
     * Installs the run script into the nodes directory.
     */
    private fun installRunScript() {
        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "net/corda/plugins/runnodes.jar"))
                fileMode = Cordformation.executableFileMode
                into("$directory/")
            }
        }

        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "net/corda/plugins/runnodes"))
                // Replaces end of line with lf to avoid issues with the bash interpreter and Windows style line endings.
                filter(mapOf("eol" to FixCrLfFilter.CrLf.newInstance("lf")), FixCrLfFilter::class.java)
                fileMode = Cordformation.executableFileMode
                into("$directory/")
            }
        }

        project.copy {
            it.apply {
                from(Cordformation.getPluginFile(project, "net/corda/plugins/runnodes.bat"))
                into("$directory/")
            }
        }
    }

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.info("Running Cordform task")
        initializeConfiguration()
        nodes.forEach(Node::installConfig)
        installCordaJar()
        installRunScript()
        bootstrapNetwork()
        nodes.forEach(Node::build)
    }
}
