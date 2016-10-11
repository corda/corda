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

