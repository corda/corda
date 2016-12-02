package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.Project

/**
 * A utility plugin that when applied will automatically create source and javadoc publishing tasks
 * To apply this plugin you must also add 'com.jfrog.bintray.gradle:gradle-bintray-plugin:1.4' to your
 * buildscript's classpath dependencies.
 *
 * To use this plugin you can add a new configuration block (extension) to your root build.gradle. See the fields
 * in BintrayConfigExtension.
 */
class PublishTasks implements Plugin<Project> {
    void apply(Project project) {
        if(project.hasProperty('classes')) {
            project.task("sourceJar", type: Jar, dependsOn: project.classes) {
                classifier = 'sources'
                from project.sourceSets.main.allSource
            }
        }

        if(project.hasProperty('javadoc')) {
            project.task("javadocJar", type: Jar, dependsOn: project.javadoc) {
                classifier = 'javadoc'
                from project.javadoc.destinationDir
            }
        }

        project.extensions.create("bintrayConfig", BintrayConfigExtension)

        def bintrayConfig = project.rootProject.extensions.findByName('bintrayConfig')
        if((bintrayConfig != null) && (bintrayConfig.publications)) {
            project.configure(project) {
                apply plugin: 'com.jfrog.bintray'
            }
            def bintray = project.extensions.findByName("bintray")
            println(bintrayConfig.publications.findAll { it == project.name })

            project.logger.info("Configuring bintray for ${project.name}")
            bintray.user = bintrayConfig.user
            bintray.key = bintrayConfig.key
            bintray.publications = bintrayConfig.publications.findAll { it == project.name }
            bintray.dryRun = bintrayConfig.dryRun ?: false
            bintray.pkg.repo = bintrayConfig.repo
            bintray.pkg.name = project.name
            bintray.pkg.userOrg = bintrayConfig.org
            bintray.pkg.licenses = bintrayConfig.licenses
            bintray.pkg.version.gpg.sign = bintrayConfig.gpgSign ?: false
            bintray.pkg.version.gpg.passphrase = bintrayConfig.gpgPassphrase
        }
    }
}
