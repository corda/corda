package net.corda.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * The Cordformation plugin deploys nodes to a directory in a state ready to be used by a developer for experimentation,
 * testing, and debugging. It will prepopulate several fields in the configuration and create a simple node runner.
 */
class Cordformation implements Plugin<Project> {
    void apply(Project project) {
        Configuration cordappConf = project.configurations.create("cordapp")
        cordappConf.transitive = false
        project.configurations.compile.extendsFrom cordappConf

        configureCordappJar(project)
    }

    /**
     * Configures this project's JAR as a Cordapp JAR
     */
    private void configureCordappJar(Project project) {
        // Note: project.afterEvaluate did not have full dependency resolution completed, hence a task is used instead
        def task = project.task('configureCordappFatJar') {
            doLast {
                project.tasks.jar.from getDirectNonCordaDependencies(project).collect { project.zipTree(it) }.flatten()
            }
        }
        project.tasks.jar.dependsOn task
    }

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

    static def getDirectNonCordaDependencies(Project project) {
        def coreCordaNames = ['jfx', 'mock', 'rpc', 'core', 'corda', 'cordform-common', 'corda-webserver', 'finance', 'node', 'node-api', 'node-schemas', 'test-utils', 'jackson', 'verifier', 'webserver', 'capsule', 'webcapsule']
        def excludes = coreCordaNames.collect { [group: 'net.corda', name: it] } + [
                [group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib'],
                [group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib-jre8'],
                [group: 'co.paralleluniverse', name: 'quasar-core']
        ]
        // The direct dependencies of this project
        def cordappDeps = project.configurations.cordapp.allDependencies
        def directDeps = project.configurations.runtime.allDependencies - cordappDeps
        // We want to filter out anything Corda related or provided by Corda, like kotlin-stdlib and quasar
        def filteredDeps = directDeps.findAll { excludes.collect { exclude -> (exclude.group == it.group) && (exclude.name == it.name) }.findAll { it }.isEmpty() }
        filteredDeps.each {
            // net.corda may be a core dependency which shouldn't be included in this cordapp so give a warning
            if(it.group.contains('net.corda')) {
                project.logger.warn("Including a dependency with a net.corda group: $it")
            } else {
                project.logger.trace("Including dependency: $it")
            }
        }
        return filteredDeps.collect { project.configurations.runtime.files it }.flatten().toSet()
    }
}
