Building a CorDapp
==================

.. contents::

Cordapps run on the Corda platform and integrate with it and each other. This article explains how to build CorDapps.
To learn what a CorDapp is, please read :doc:`cordapp-overview`.

CorDapp format
--------------
A CorDapp is a semi-fat JAR that contains all of the CorDapp's dependencies *except* the Corda core libraries and any
other CorDapps it depends on.

For example, if a Cordapp depends on ``corda-core``, ``your-other-cordapp`` and ``apache-commons``, then the Cordapp
JAR will contain:

* All classes and resources from the ``apache-commons`` JAR and its dependencies
* *Nothing* from the other two JARs

Build tools
-----------
In the instructions that follow, we assume you are using ``gradle`` and the ``cordformation`` plugin to build your
CorDapp. See the `example build file <https://github.com/corda/cordapp-template-kotlin/blob/release-V1/build.gradle>`_
from the CorDapp template.

Setting your dependencies
-------------------------

Choosing your Corda version
^^^^^^^^^^^^^^^^^^^^^^^^^^^
The following two lines of the ``build.gradle`` file define the Corda version used to build your CorDapp:

.. sourcecode:: groovy

    ext.corda_release_version = '1.0.0'
    ext.corda_gradle_plugins_version = '1.0.0'

In this case, our CorDapp will use:

* Version 1.0 of Corda
* Version 1.0 of the Corda gradle plugins

You can find the latest published version of both here: https://bintray.com/r3/corda.

``corda_gradle_plugins_versions`` are given in the form ``major.minor.patch``. You should use the same ``major`` and
``minor`` versions as the Corda version you are using, and the latest ``patch`` version. A list of all the available
versions can be found here: https://bintray.com/r3/corda/cordapp.

In certain cases, you may also wish to build against the unstable Master branch. See :doc:`building-against-master`.

Corda dependencies
^^^^^^^^^^^^^^^^^^
The ``cordformation`` plugin adds:

* ``cordaCompile`` as a new configuration that ``compile`` extends from
* ``cordaRuntime`` which ``runtime`` extends from

To build against Corda you must add the following to your ``build.gradle`` file;

* The ``net.corda:corda:<version>`` JAR as a ``cordaRuntime`` dependency
* Each compile dependency (eg ``corda-core``) as a ``cordaCompile`` dependency

To use Corda's test facilities you must add ``net.corda:corda-test-utils:<version>`` as a ``testCompile`` dependency
(i.e. a default Java/Kotlin test compile task).

.. warning:: Never include ``corda-test-utils`` as a ``compile`` or ``cordaCompile`` dependency.

Dependencies on other CorDapps
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Sometimes, a CorDapp you build will depend on states, contracts or flows defined in another CorDapp. You must include
the CorDapp your CorDapp depends upon as a ``cordapp`` dependency in your ``build.gradle`` file.

Other dependencies
^^^^^^^^^^^^^^^^^^
If your CorDapps have any additional external dependencies, they can be specified like normal Kotlin/Java dependencies
in Gradle. See the example below, specifically the ``apache-commons`` include.

For further information about managing dependencies, see
`the Gradle docs <https://docs.gradle.org/current/userguide/dependency_management.html>`_.

Example
^^^^^^^
The following is a sample of what a gradle dependencies block for a CorDapp could look like. The CorDapp template
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
            // Identifying a CorDapp by its module in the same project.
            cordapp project(":cordapp-contracts-states")
            // Identifying a CorDapp by its fully-qualified name.
            cordapp "net.corda:bank-of-corda-demo:1.0"

            // Some other dependencies
            compile "org.jetbrains.kotlin:kotlin-stdlib-jre8:$kotlin_version"
            testCompile "org.jetbrains.kotlin:kotlin-test:$kotlin_version"
            testCompile "junit:junit:$junit_version"

            compile "org.apache.commons:commons-lang3:3.6"
        }

Creating the CorDapp JAR
------------------------
The gradle ``jar`` task included in the CorDapp template build file will automatically build your CorDapp JAR correctly
as long as your dependencies are set correctly.

.. warning:: The hash of the generated CorDapp JAR is not deterministic, as it depends on variables such as the
   timestamp at creation. Nodes running the same CorDapp must therefore ensure they are using the exact same CorDapp
   jar, and not different versions of the JAR created from identical sources.

The filename of the JAR must include a unique identifier to deduplicate it from other releases of the same CorDapp.
This is typically done by appending the version string to the CorDapp's name. This unique identifier should not change
once the JAR has been deployed on a node. If it does, make sure no one is relying on ``FlowContext.appName`` in their
flows (see :doc:`versioning`).

Installing the CorDapp jar
--------------------------

.. note:: Before installing a CorDapp, you must create one or more nodes to install it on. For instructions, please see
   :doc:`generating-a-node`.

At runtime, nodes will load any CorDapps present in their ``cordapps`` folder. Therefore in order to install a CorDapp on
a node, the CorDapp JAR must be added to the ``<node_dir>/cordapps/`` folder, where ``node_dir`` is the folder in which
the node's JAR and configuration files are stored.

The ``deployNodes`` gradle task, if correctly configured, will automatically place your CorDapp JAR as well as any
dependent CorDapp JARs specified into the ``cordapps`` folder automatically.