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
``ext.corda_release_version`` and ``ext.corda_gradle_plugins_version`` are used in the ``build.gradle`` to define the
versions of Corda and the Corda Gradle Plugins that are used to build your CorDapp.

For example, to use version 1.0 of Corda and version 1.0 of the Corda gradle plugins, you'd write:

.. sourcecode:: groovy

    ext.corda_release_version = '1.0.0'
    ext.corda_gradle_plugins_version = '1.0.0'

You can find the latest published version of both here: https://bintray.com/r3/corda.

``corda_gradle_plugins_versions`` are given in the form ``major.minor.patch``. You should use the same ``major`` and
``minor`` versions as the Corda version you are using, and the latest ``patch`` version. A list of all the available
versions can be found here: https://bintray.com/r3/corda/cordapp.

In certain cases, you may also wish to build against the unstable Master branch. See :doc:`building-against-master`.

Corda dependencies
^^^^^^^^^^^^^^^^^^
The ``cordformation`` plugin adds two new gradle configurations:

* ``cordaCompile``, which extends ``compile``
* ``cordaRuntime``, which extends ``runtime``

To build against Corda, you must add the following to your ``build.gradle`` file:

* ``net.corda:corda:$corda_release_version`` as a ``cordaRuntime`` dependency
* Each Corda compile dependency (eg ``net.corda:corda-core:$corda_release_version``) as a ``cordaCompile`` dependency

You may also want to add:

* ``net.corda:corda-test-utils:$corda_release_version`` as a ``testCompile`` dependency, in order to use Corda's test
  frameworks
* ``net.corda:corda-webserver:$corda_release_version`` as a ``cordaRuntime`` dependency, in order to use Corda's
  built-in development webserver

.. warning:: Never include ``corda-test-utils`` as a ``compile`` or ``cordaCompile`` dependency.

Dependencies on other CorDapps
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
You CorDapp may also depend on classes defined in another CorDapp, such as states, contracts and flows. There are two
ways to add another CorDapp as a dependency in your CorDapp's ``build.gradle`` file:

* ``cordapp project(":another-cordapp")`` (use this if the other CorDapp is defined in a module in the same project)
* ``cordapp "net.corda:another-cordapp:1.0"`` (use this otherwise)

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
Once your dependencies are set correctly, you can build your CorDapp JAR using the gradle ``jar`` task:

* Unix/Mac OSX: ``./gradlew jar``

* Windows: ``gradlew.bat jar``

The CorDapp JAR will be output to the ``build/libs`` folder.

.. warning:: The hash of the generated CorDapp JAR is not deterministic, as it depends on variables such as the
   timestamp at creation. Nodes running the same CorDapp must therefore ensure they are using the exact same CorDapp
   JAR, and not different versions of the JAR created from identical sources.

The filename of the JAR must include a unique identifier to deduplicate it from other releases of the same CorDapp.
This is typically done by appending the version string to the CorDapp's name. This unique identifier should not change
once the JAR has been deployed on a node. If it does, make sure no one is relying on ``FlowContext.appName`` in their
flows (see :doc:`versioning`).

Installing the CorDapp JAR
--------------------------

.. note:: Before installing a CorDapp, you must create one or more nodes to install it on. For instructions, please see
   :doc:`generating-a-node`.

At runtime, nodes will load any CorDapps present in their ``cordapps`` folder. Therefore in order to install a CorDapp on
a node, the CorDapp JAR must be added to the ``<node_dir>/cordapps/`` folder, where ``node_dir`` is the folder in which
the node's JAR and configuration files are stored.