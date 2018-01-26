Publish Utils
=============

Publishing utilities adds a couple of tasks to any project it is applied to that hide some boilerplate that would
otherwise be placed in the Cordapp template's build.gradle.

There are two tasks exposed: `sourceJar` and `javadocJar` and both return a `FileCollection`.

It is used within the `publishing` block of a build.gradle as such;

.. code-block:: text

    // This will publish the sources, javadoc, and Java components to Maven.
    // See the `maven-publish` plugin for more info: https://docs.gradle.org/current/userguide/publishing_maven.html
    publishing {
        publications {
            jarAndSources(MavenPublication) {
                from components.java
                // The two lines below are the tasks added by this plugin.
                artifact sourceJar
                artifact javadocJar
            }
        }
    }

Bintray Publishing
------------------

For large multibuild projects it can be inconvenient to store the entire configuration for bintray and maven central
per project (with a bintray and publishing block with extended POM information). Publish utils can bring the number of
configuration blocks down to one in the ideal scenario.

To use this plugin you must first apply it to both the root project and any project that will be published with

.. code-block:: text

    apply plugin: 'net.corda.plugins.publish-utils'

Next you must setup the general bintray configuration you wish to use project wide, for example:

.. code-block:: text

    bintrayConfig {
        user = <your bintray username>
        key = <your bintray user key>
        repo = 'example repo'
        org = 'example organisation'
        licenses = ['a license']
        vcsUrl = 'https://example.com'
        projectUrl = 'https://example.com'
        gpgSign = true // Whether to GPG sign
        gpgPassphrase = <your bintray GPG key passphrase> // Only required if gpgSign is true and your key is passworded
        publications = ['example'] // a list of publications (see below)
        license {
            name = 'example'
            url = 'https://example.com'
            distribution = 'repo'
        }
        developer {
            id = 'a developer id'
            name = 'a developer name'
            email = 'example@example.com'
        }
    }

.. note:: You can currently only have one license and developer in the maven POM sections

**Publications**

This plugin assumes, by default, that publications match the name of the project. This means, by default, you can
just list the names of the projects you wish to publish (e.g. to publish `test:myapp` you need  `publications = ['myapp']`.
If a project requires a different name you can configure it *per project* with the project configuration block.

The project configuration block has the following structure:

.. code-block:: text

    publish {
        disableDefaultJar = false // set to true to disable the default JAR being created (e.g. when creating a fat JAR)
        name 'non-default-project-name' // Always put this last because it causes configuration to happen
    }

**Artifacts**

To add additional artifacts to the project you can use the default gradle `artifacts` block with the `publish`
configuration. For example:

    artifacts {
         publish buildFatJar {
            // You can configure this as a regular maven publication
         }
    }
