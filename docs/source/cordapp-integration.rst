Integrating Cordapps
====================

Cordapps run on the Corda platform and integrate with it and each other. To learn more about the basics of a Cordapp
please read :doc:`cordapp-overview`. To learn about writing a Cordapp as a developer please read :doc:`writing-cordapps`.

This article will specifically deal with build systems.

Cordapp JAR Format
------------------

The first step to integrating a Cordapp with Corda is to ensure it is in the correct format. The correct format of a JAR
is a semi-fat JAR that contains all of its own dependencies *except* the Corda core libraries and other Cordapps.

For example if your Cordapp depends on ``corda-core``, ``your-other-cordapp`` and ``apache-commons`` then the Cordapp
JAR will contain all classes and resources from the ``apache-commons`` JAR and its dependencies and *nothing* from the
other two JARs.

.. note:: The rest of this tutorial assumes you are using ``gradle``, the ``cordformation`` plugin and have forked from
          one of our cordapp templates.

The ``jar`` task included by default in the cordapp templates will automatically build your JAR in this format as long
as your dependencies are correctly set.

Building against Corda
----------------------

To build against Corda you must do the following to your ``build.gradle``;

* Add the ``net.corda:corda:<version>`` JAR as a ``cordaRuntime`` dependency.
* Add each compile dependency (eg ``corda-core``) as a ``corda`` dependency.

To make use of the Corda test facilities you must;

* Add ``net.corda:corda-test-utils:<version>`` as a ``testCompile`` dependency (eg; a default Java/Kotlin compile task).

.. warning:: Never include ``corda-test-utils`` as a ``compile`` or ``corda`` dependency.

These configurations work by the ``cordformation`` plugin adding ``corda`` as a new configuration that ``compile``
extends from, and ``cordaRuntime`` which ``runtime`` extends from.

Building against Cordapps
-------------------------

To build against a Cordapp you must add it as a ``cordapp`` dependency to your ``build.gradle``.

Installing CorDapps
-------------------

At runtime, nodes will load any plugins present in their ``plugins`` folder. Therefore in order to install a cordapp to
a node the cordapp JAR must be added to the ``<node_dir>/plugins/`` folder, where ``node_dir`` is the folder in which the
node's JAR and configuration files are stored).

The ``deployNodes`` gradle task, if correctly configured, will automatically place your cordapp JAR as well as any
dependent cordapp JARs specified into the directory automatically.

Example
-------

The following is a sample of what a gradle dependencies block for a cordapp could look like. The cordapp template
is already correctly configured and this is for reference only;

.. container:: codeset

    .. sourcecode:: groovy

        dependencies {
            // Corda integration dependencies
            corda "net.corda:corda-core:$corda_release_version"
            corda "net.corda:corda-finance:$corda_release_version"
            corda "net.corda:corda-jackson:$corda_release_version"
            corda "net.corda:corda-rpc:$corda_release_version"
            corda "net.corda:corda-node-api:$corda_release_version"
            corda "net.corda:corda-webserver-impl:$corda_release_version"
            cordaRuntime "net.corda:corda:$corda_release_version"
            cordaRuntime "net.corda:corda-webserver:$corda_release_version"
            testCompile "net.corda:corda-test-utils:$corda_release_version"

            // Corda Plugins: dependent flows and services
            cordapp "net.corda:bank-of-corda-demo:1.0"

            // Some other dependencies
            compile "org.jetbrains.kotlin:kotlin-stdlib-jre8:$kotlin_version"
            testCompile "org.jetbrains.kotlin:kotlin-test:$kotlin_version"
            testCompile "junit:junit:$junit_version"

            compile "org.graphstream:gs-core:1.3"
            compile("org.graphstream:gs-ui:1.3") {
                exclude group: "bouncycastle"
            }
        }

