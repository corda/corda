package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.artifacts.*

/**
 * The Cordapp plugin will turn a project into a cordapp project which builds cordapp JARs with the correct format
 * and with the information needed to run on Corda.
 */
class CordappPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        Utils.createCompileConfiguration("cordapp", project)
        Utils.createCompileConfiguration("cordaCompile", project)

        Configuration configuration = project.configurations.create("cordaRuntime")
        configuration.transitive = false
        project.configurations.runtime.extendsFrom configuration

        configureCordappJar(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR
     */
    private void configureCordappJar(Project project) {
        // Note: project.afterEvaluate did not have full dependency resolution completed, hence a task is used instead
        Task task = project.task('configureCordappFatJar') {
            doLast {
                project.tasks.jar.from(getDirectNonCordaDependencies(project).collect { project.zipTree(it)}) {
                    exclude "META-INF/*.SF"
                    exclude "META-INF/*.DSA"
                    exclude "META-INF/*.RSA"
                }
            }
        }
        project.tasks.jar.dependsOn(task)
    }

    private static Set<File> getDirectNonCordaDependencies(Project project) {
        List<Map<String, String>> excludes = [
                [group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib'],
                [group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib-jre8'],
                [group: 'org.jetbrains.kotlin', name: 'kotlin-reflect'],
                [group: 'co.paralleluniverse', name: 'quasar-core']
        ]

        project.with {
            // The direct dependencies of this project
            Set<Dependency> excludeDeps = configurations.cordapp.allDependencies + configurations.cordaCompile.allDependencies + configurations.cordaRuntime.allDependencies
            Set<Dependency> directDeps = configurations.runtime.allDependencies - excludeDeps
            // We want to filter out anything Corda related or provided by Corda, like kotlin-stdlib and quasar
            Set<Dependency> filteredDeps = directDeps.findAll { excludes.collect { exclude -> (exclude.group == it.group) && (exclude.name == it.name) }.findAll { it }.isEmpty() }
            filteredDeps.each {
                // net.corda or com.r3.corda.enterprise may be a core dependency which shouldn't be included in this cordapp so give a warning
                if (it.group && (it.group.startsWith('net.corda.') || it.group.startsWith('com.r3.corda.enterprise.'))) {
                    logger.warn("You appear to have included a Corda platform component ($it) using a 'compile' or 'runtime' dependency." +
                            "This can cause node stability problems. Please use 'corda' instead." +
                            "See http://docs.corda.net/cordapp-build-systems.html")
                } else {
                    logger.info("Including dependency in CorDapp JAR: $it")
                }
            }
            return filteredDeps.collect { configurations.runtime.files(it) }.flatten().toSet()
        }
    }
}
