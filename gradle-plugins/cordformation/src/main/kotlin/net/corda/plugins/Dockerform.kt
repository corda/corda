package net.corda.plugins

import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.DumperOptions
import java.nio.file.Path
import java.nio.file.Paths
import org.yaml.snakeyaml.Yaml
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Creates docker-compose file and image definitions based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Dockerform : Baseform() {
    private companion object {
        val nodeJarName = "corda.jar"
        private val defaultDirectory: Path = Paths.get("build", "docker")

        private val dockerComposeFileVersion = "3"

        private val  yamlOptions = DumperOptions().apply {
            indent = 2
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        }
        private val yaml = Yaml(yamlOptions)
    }

    private val directoryPath = project.projectDir.toPath().resolve(directory)

    val dockerComposePath = directoryPath.resolve("docker-compose.yml")

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.info("Running Cordform task")
        initializeConfiguration()
        nodes.forEach(Node::installDockerConfig)
        installCordaJar()
        bootstrapNetwork()
        nodes.forEach(Node::buildDocker)


        // Transform nodes path the absolute ones
        val services = nodes.map { it.containerName to mapOf(
                "build" to directoryPath.resolve(it.nodeDir.name).toAbsolutePath().toString(),
                "ports" to listOf(it.rpcPort)) }.toMap()


        val dockerComposeObject = mapOf(
                "version" to dockerComposeFileVersion,
                "services" to services)

        val dockerComposeContent = yaml.dump(dockerComposeObject)

        Files.write(dockerComposePath, dockerComposeContent.toByteArray(StandardCharsets.UTF_8))
    }
}
