Cordapp Plugins
===============

Building Plugins
----------------

To build the plugins that Cordapps require run the following from the root of the Corda project:

.. code-block:: text

    ./gradlew publishToMavenLocal

The plugins will now be installed to MavenLocal.

Installing Plugins
------------------

To use the plugins, if you are not already using the Cordapp template project, you must modify your build.gradle. Add
the following segments to the relevant part of your build.gradle.

.. code-block::

    buildscript {
        ext.corda_version = '<enter the corda version you build against here>'
        ... (your buildscript)

        repositories {
            ... (other repositories)
            mavenLocal()
        }

        dependencies {
            ... (your dependencies)
            classpath "com.r3corda.plugins:<plugin-maven-name>:$corda_version"
        }
    }

    apply plugin: 'com.r3corda.plugins.<plugin-maven-name>'

    ...

Cordformation
-------------

Plugin Maven Name::

    cordformation

Cordformation is the local node deployment system for Cordapps, the nodes generated are intended to be used for
experimenting, debugging, and testing node configurations and setups but not intended for production or testnet
deployment.

To use this plugin you must add a new task that is of the type `com.r3corda.plugins.Cordform` and then configure
the nodes you wish to deploy with the Node and nodes configuration DSL. This DSL is specified in the JavaDoc but
an example of this is in the template-cordapp and below is a three node example;

.. code-block:: text

    task deployNodes(type: com.r3corda.plugins.Cordform, dependsOn: ['build']) {
        directory "./build/nodes" // The output directory
        networkMap "Notary" // This will resolve a node in this configuration
        node {
            name "Notary"
            dirName "notary"
            nearestCity "London"
            notary true // Sets this node to be a notary
            advertisedServices = []
            artemisPort 12345
            webPort 12346
            cordapps = []
        }
        node {
            name "NodeA"
            dirName "nodea"
            nearestCity "London"
            advertisedServices = []
            artemisPort 31337
            webPort 31339
            cordapps = []
        }
        node {
            name "NodeB"
            dirName "nodeb"
            nearestCity "New York"
            advertisedServices = []
            artemisPort 31338
            webPort 31340
            cordapps = []
        }
    }

Because it is a task you can create multiple tasks with multiple configurations that you use commonly.

New nodes can be added by simply adding another node block and giving it a different name, directory and ports. When you
run this task it will install the nodes to the directory specified and a script will be generated (for *nix users only
at present) to run the nodes with one command.

Other cordapps can also be specified if they are already specified as classpath or compile dependencies in your
build.gradle.

Publish Utils
-------------

Plugin Maven Name::

    publish-utils

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

Quasar Utils
------------

Plugin Maven Name::

    quasar-utils

Quasar utilities adds several tasks and configuration that provide a default Quasar setup and removes some boilerplate.
One line must be added to your build.gradle once you apply this plugin:

.. code-block:: text

    quasarScan.dependsOn('classes')

If any sub-projects are added that this project depends on then add the gradle target for that project to the depends
on statement. eg:

.. code-block:: text

    quasarScan.dependsOn('classes', 'subproject:subsubproject', ...)


