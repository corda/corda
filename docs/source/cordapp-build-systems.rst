CorDapp Build Systems
=====================

Cordapps run on the Corda platform and integrate with it and each other. To learn more about the basics of a Cordapp
please read :doc:`cordapp-overview`. To learn about writing a Cordapp as a developer please read :doc:`writing-cordapps`.

This article will specifically deal with how to build cordapps, specifically with Gradle.

CorDapp JAR format
------------------

The first step to integrating a Cordapp with Corda is to ensure it is in the correct format. The correct format of a JAR
is a semi-fat JAR that contains all of its own dependencies *except* the Corda core libraries and other Cordapps.

For example if your Cordapp depends on ``corda-core``, ``your-other-cordapp`` and ``apache-commons`` then the Cordapp
JAR will contain all classes and resources from the ``apache-commons`` JAR and its dependencies and *nothing* from the
other two JARs.

.. note:: The rest of this tutorial assumes you are using ``gradle``, the ``cordapp`` plugin and have forked from
          one of our cordapp templates.

The ``jar`` task included by default in the cordapp templates will automatically build your JAR in this format as long
as your dependencies are correctly set.

The filename of the jar must include some sort of unique identifier to deduplicate it from other releases of the same
CorDapp. This is typically done by appending the version string. It should not change once the jar has been deployed on
a node. If it is then make sure no one is checking ``FlowContext.appName`` (see :doc:`versioning`).

Building against Corda
----------------------

To build against Corda you must do the following to your ``build.gradle``;

* Add the ``net.corda:corda:<version>`` JAR as a ``cordaRuntime`` dependency.
* Add each compile dependency (eg ``corda-core``) as a ``cordaCompile`` dependency.

To make use of the Corda test facilities you must;

* Add ``net.corda:corda-test-utils:<version>`` as a ``testCompile`` dependency (eg; a default Java/Kotlin compile task).

.. warning:: Never include ``corda-test-utils`` as a ``compile`` or ``cordaCompile`` dependency.

These configurations work by the ``cordapp`` plugin adding ``cordaCompile`` as a new configuration that ``compile``
extends from, and ``cordaRuntime`` which ``runtime`` extends from.

Choosing your Corda version
^^^^^^^^^^^^^^^^^^^^^^^^^^^
The following two lines of the ``build.gradle`` file define the Corda version used to build your CorDapp:

.. sourcecode:: groovy

    ext.corda_release_version = '0.13.0'
    ext.corda_gradle_plugins_version = '0.13.3'

In this case, our CorDapp will use the Milestone 13 release of Corda, and version 13.3 of the Corda gradle plugins. You
can find the latest published version of both here: https://bintray.com/r3/corda.

``corda_gradle_plugins_versions`` are given in the form ``major.minor.patch``. You should use the same ``major`` and
``minor`` versions as the Corda version you are using, and the latest ``patch`` version. A list of all the available
versions can be found here: https://bintray.com/r3/corda/cordapp.

In certain cases, you may also wish to build against the unstable Master branch. See :doc:`building-against-master`.

Building against CorDapps
-------------------------

To build against a Cordapp you must add it as a ``cordapp`` dependency to your ``build.gradle``.

Other Dependencies
------------------

If your CorDapps have any additional external dependencies, they can be specified like normal Kotlin/Java dependencies
in Gradle. See the example below, specifically the ``apache-commons`` include.

For further information about managing dependencies, see
`the Gradle docs <https://docs.gradle.org/current/userguide/dependency_management.html>`_.

Installing CorDapps
-------------------

At runtime, nodes will load any CorDapp JARs present in their ``cordapps`` folder. Therefore in order to install a CorDapp to
a node the CorDapp JAR must be added to the ``<node_dir>/cordapps/`` folder, where ``node_dir`` is the folder in which the
node's JAR and configuration files are stored).

The ``deployNodes`` gradle task, if correctly configured, will automatically place your CorDapp JAR as well as any
dependent cordapp JARs specified into the directory automatically.

Example
-------

The following is a sample of what a gradle dependencies block for a cordapp could look like. The cordapp template
is already correctly configured and this is for reference only;

.. container:: codeset

    .. sourcecode:: groovy

        dependencies {
            // Corda integration dependencies
            cordaCompile "net.corda:corda-core:$corda_release_version"
            cordaCompile "net.corda:corda-finance:$corda_release_version"
            cordaCompile "net.corda:corda-jackson:$corda_release_version"
            cordaCompile "net.corda:corda-rpc:$corda_release_version"
            cordaCompile "net.corda:corda-node-api:$corda_release_version"
            cordaCompile "net.corda:corda-webserver-impl:$corda_release_version"
            cordaRuntime "net.corda:corda:$corda_release_version"
            cordaRuntime "net.corda:corda-webserver:$corda_release_version"
            testCompile "net.corda:corda-test-utils:$corda_release_version"

            // Corda Plugins: dependent flows and services
            cordapp "net.corda:bank-of-corda-demo:1.0"

            // Some other dependencies
            compile "org.jetbrains.kotlin:kotlin-stdlib-jre8:$kotlin_version"
            testCompile "org.jetbrains.kotlin:kotlin-test:$kotlin_version"
            testCompile "junit:junit:$junit_version"

            compile "org.apache.commons:commons-lang3:3.6"
        }

