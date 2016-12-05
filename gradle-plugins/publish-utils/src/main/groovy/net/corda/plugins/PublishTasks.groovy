package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.MavenPom

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

        def bintrayConfig = project.rootProject.extensions.findByName('bintrayConfig')
        if((bintrayConfig != null) && (bintrayConfig.publications)) {
            def publications = bintrayConfig.publications.findAll { it == project.name }
            println("HI")
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

                    extendPomForMavenCentral(pom)
                }
            }
        }
    }

    // Maven central requires all of the below fields for this to be a valid POM
    void extendPomForMavenCentral(MavenPom pom) {
        pom.withXml {
            asNode().children().last() + {
                resolveStrategy = Closure.DELEGATE_FIRST
                name project.name
                description project.description
                url 'https://github.com/corda/corda'
                scm {
                    url 'https://github.com/corda/corda'
                }

                licenses {
                    license {
                        name 'Apache-2.0'
                        url 'https://www.apache.org/licenses/LICENSE-2.0'
                        distribution 'repo'
                    }
                }

                developers {
                    developer {
                        id 'R3'
                        name 'R3'
                        email 'dev@corda.net'
                    }
                }
            }
        }
    }

    void configureBintray(def bintray, def bintrayConfig) {
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
