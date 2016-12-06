package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.MavenPom
import net.corda.plugins.bintray.*

/**
 * A utility plugin that when applied will automatically create source and javadoc publishing tasks
 * To apply this plugin you must also add 'com.jfrog.bintray.gradle:gradle-bintray-plugin:1.4' to your
 * buildscript's classpath dependencies.
 *
 * To use this plugin you can add a new configuration block (extension) to your root build.gradle. See the fields
 * in BintrayConfigExtension.
 */
class PublishTasks implements Plugin<Project> {
    Project project

    void apply(Project project) {
        this.project = project

        createTasks()
        createExtensions()

        def bintrayConfig = project.rootProject.extensions.findByType(BintrayConfigExtension.class)
        if((bintrayConfig != null) && (bintrayConfig.publications)) {
            def publications = bintrayConfig.publications.findAll { it == project.name }
            if(publications.size > 0) {
                project.logger.info("Configuring bintray for ${project.name}")
                project.configure(project) {
                    apply plugin: 'com.jfrog.bintray'
                }
                def bintray = project.extensions.findByName("bintray")
                configureBintray(bintray, bintrayConfig)
                project.publishing.publications.create(project.name, MavenPublication) {
                    from project.components.java
                    groupId  project.group
                    artifactId project.name

                    artifact project.tasks.sourceJar
                    artifact project.tasks.javadocJar

                    extendPomForMavenCentral(pom, bintrayConfig)
                }
            }
        }
    }

    // Maven central requires all of the below fields for this to be a valid POM
    void extendPomForMavenCentral(MavenPom pom, BintrayConfigExtension config) {
        pom.withXml {
            asNode().children().last() + {
                resolveStrategy = Closure.DELEGATE_FIRST
                name project.name
                description project.description
                url config.projectUrl
                scm {
                    url config.vcsUrl
                }

                licenses {
                    license {
                        name config.license.name
                        url config.license.url
                        distribution config.license.url
                    }
                }

                developers {
                    developer {
                        id config.developer.id
                        name config.developer.name
                        email config.developer.email
                    }
                }
            }
        }
    }

    void configureBintray(def bintray, BintrayConfigExtension bintrayConfig) {
        bintray.user = bintrayConfig.user
        bintray.key = bintrayConfig.key
        bintray.publications = [ project.name ]
        bintray.dryRun = bintrayConfig.dryRun ?: false
        bintray.pkg.repo = bintrayConfig.repo
        bintray.pkg.name = project.name
        bintray.pkg.userOrg = bintrayConfig.org
        bintray.pkg.licenses = bintrayConfig.licenses
        bintray.pkg.version.gpg.sign = bintrayConfig.gpgSign ?: false
        bintray.pkg.version.gpg.passphrase = bintrayConfig.gpgPassphrase
    }

    void createTasks() {
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
    }

    void createExtensions() {
        if(project == project.rootProject) {
            project.extensions.create("bintrayConfig", BintrayConfigExtension)
        }
    }
}
