package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.Project

/**
 * A utility plugin that when applied will automatically create source and javadoc publishing tasks
 * To apply this plugin you must also add 'com.jfrog.bintray.gradle:gradle-bintray-plugin:1.4' to your
 * buildscript's classpath dependencies.
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
        project.extensions.create("bintrayPublish", BintrayPublishExtension)

        def bintrayValues = project.extensions.findByName("bintrayPublish")
        def bintrayConfig = project.rootProject.extensions.findByName('bintrayConfig')
        if((bintrayConfig != null) && (bintrayValues != null)) {
            // TODO AM:
            // Problem 1. Bootstrapping - do not want root to depend on this project
            // Problem 2. This project's extension is not available here
            // Problem 3. Bintray's extension is already configured after evaluation
            // Possible solutions:
            // name: project.name
            // publications: project.name (make it a forced convention)
            // dryRun: move to root.
            // Problem 4: Root project therefore cannot be published
            // Solution: Why use this plugin if you only have a root project?
            project.configure(project) {
                apply plugin: 'com.jfrog.bintray'
            }
            def bintray = project.extensions.findByName("bintray")

            project.logger.info("Configuring bintray for ${project.name}")
            bintray.user = bintrayConfig.user
            bintray.key = bintrayConfig.key
            bintray.publications = bintrayValues.publications
            bintray.dryRun = bintrayValues.dryRun ?: false
            bintray.pkg.repo = bintrayConfig.repo
            bintray.pkg.name = bintrayValues.name ?: project.name
            bintray.pkg.userOrg = bintrayConfig.org
            bintray.pkg.licenses = bintrayConfig.licenses
            bintray.pkg.version.gpg.sign = bintrayConfig.gpgSign ?: false
            bintray.pkg.version.gpg.passphrase = bintrayConfig.gpgPassphrase
        }
    }
}
