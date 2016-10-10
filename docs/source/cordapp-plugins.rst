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

The available plugins are in the gradle-plugins directory of the Corda repository.