@file:Suppress("UNUSED")
package net.corda.gradle.jarfilter

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * This plugin definition is only needed by the tests.
 */
class JarFilterPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.info("Applying JarFilter plugin")
    }
}
