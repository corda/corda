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
    String publishName
    ProjectPublishExtension publishConfig

    void apply(Project project) {
        this.project = project
        this.publishName = project.name

        createTasks()
        createExtensions()
        createConfigurations()
    }

    void setPublishName(String publishName) {
        project.logger.info("Changing publishing name from ${project.name} to ${publishName}")
        this.publishName = publishName
        checkAndConfigurePublishing()
    }

    void checkAndConfigurePublishing() {
        project.logger.info("Checking whether to publish $publishName")
        def bintrayConfig = project.rootProject.extensions.findByType(BintrayConfigExtension.class)
        if((bintrayConfig != null) && (bintrayConfig.publications) && (bintrayConfig.publications.findAll { it == publishName }.size() > 0)) {
            configurePublishing(bintrayConfig)
        }
    }

    void configurePublishing(BintrayConfigExtension bintrayConfig) {
        project.logger.info("Configuring bintray for ${publishName}")
        configureMavenPublish(bintrayConfig)
        configureBintray(bintrayConfig)
    }

    void configureMavenPublish(BintrayConfigExtension bintrayConfig) {
        project.apply([plugin: 'maven-publish'])
        project.publishing.publications.create(publishName, MavenPublication) {
            groupId project.group
            artifactId publishName

            artifact project.tasks.sourceJar
            artifact project.tasks.javadocJar

            project.configurations.publish.artifacts.each {
                project.logger.debug("Adding artifact: $it")
                delegate.artifact it
            }

            if (!publishConfig.disableDefaultJar && !publishConfig.publishWar) {
                from project.components.java
            } else if (publishConfig.publishWar) {
                from project.components.web
            }

            extendPomForMavenCentral(pom, bintrayConfig)
        }
        project.task("install", dependsOn: "publishToMavenLocal")
    }

    // Maven central requires all of the below fields for this to be a valid POM
    void extendPomForMavenCentral(MavenPom pom, BintrayConfigExtension config) {
        pom.withXml {
            asNode().children().last() + {
                resolveStrategy = Closure.DELEGATE_FIRST
                name publishName
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

    void configureBintray(BintrayConfigExtension bintrayConfig) {
        project.apply([plugin: 'com.jfrog.bintray'])
        project.bintray {
            user = bintrayConfig.user
            key = bintrayConfig.key
            publications = [ publishName ]
            dryRun = bintrayConfig.dryRun ?: false
            pkg {
                repo = bintrayConfig.repo
                name = publishName
                userOrg = bintrayConfig.org
                licenses = bintrayConfig.licenses

                version {
                    gpg {
                        sign = bintrayConfig.gpgSign ?: false
                        passphrase = bintrayConfig.gpgPassphrase
                    }
                }
            }
        }
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
        publishConfig = project.extensions.create("publish", ProjectPublishExtension)
        publishConfig.setPublishTask(this)
    }

    void createConfigurations() {
        project.configurations.create("publish")
    }
}
