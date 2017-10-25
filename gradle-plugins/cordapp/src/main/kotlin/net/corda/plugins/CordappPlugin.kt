package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.artifacts.*
import org.gradle.jvm.tasks.Jar
import java.io.File

/**
 * The Cordapp plugin will turn a project into a cordapp project which builds cordapp JARs with the correct format
 * and with the information needed to run on Corda.
 */
class CordappPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.info("Configuring ${project.name} as a cordapp")

        Utils.createCompileConfiguration("cordapp", project)
        Utils.createCompileConfiguration("cordaCompile", project)

        val configuration: Configuration = project.configurations.create("cordaRuntime")
        configuration.isTransitive = false
        project.configurations.single { it.name == "runtime" }.extendsFrom(configuration)

        configureCordappJar(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR
     */
    private fun configureCordappJar(project: Project) {
        // Note: project.afterEvaluate did not have full dependency resolution completed, hence a task is used instead
        val task = project.task("configureCordappFatJar")
        val jarTask = project.tasks.getByName("jar") as Jar
        task.doLast {
            jarTask.from(getDirectNonCordaDependencies(project).map { project.zipTree(it)}).apply {
                exclude("META-INF/*.SF")
                exclude("META-INF/*.DSA")
                exclude("META-INF/*.RSA")
            }
        }
        jarTask.dependsOn(task)
    }

    private fun getDirectNonCordaDependencies(project: Project): Set<File> {
        project.logger.info("Finding direct non-corda dependencies for inclusion in CorDapp JAR")
        val excludes = listOf(
                mapOf("group" to "org.jetbrains.kotlin", "name" to "kotlin-stdlib"),
                mapOf("group" to "org.jetbrains.kotlin", "name" to "kotlin-stdlib-jre8"),
                mapOf("group" to "org.jetbrains.kotlin", "name" to "kotlin-reflect"),
                mapOf("group" to "co.paralleluniverse", "name" to "quasar-core")
        )

        val runtimeConfiguration = project.configuration("runtime")
        // The direct dependencies of this project
        val excludeDeps = project.configuration("cordapp").allDependencies +
                project.configuration("cordaCompile").allDependencies +
                project.configuration("cordaRuntime").allDependencies
        val directDeps = runtimeConfiguration.allDependencies - excludeDeps
        // We want to filter out anything Corda related or provided by Corda, like kotlin-stdlib and quasar
        val filteredDeps = directDeps.filter { dep ->
            excludes.none { exclude -> (exclude["group"] == dep.group) && (exclude["name"] == dep.name) }
        }
        filteredDeps.forEach {
            // net.corda or com.r3.corda.enterprise may be a core dependency which shouldn't be included in this cordapp so give a warning
            if ((it.group.startsWith("net.corda.") || it.group.startsWith("com.r3.corda.enterprise."))) {
                project.logger.warn("You appear to have included a Corda platform component ($it) using a 'compile' or 'runtime' dependency." +
                        "This can cause node stability problems. Please use 'corda' instead." +
                        "See http://docs.corda.net/cordapp-build-systems.html")
            } else {
                project.logger.info("Including dependency in CorDapp JAR: $it")
            }
        }
        return filteredDeps.map { runtimeConfiguration.files(it) }.flatten().toSet()
    }
}
