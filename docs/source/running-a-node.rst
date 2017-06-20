Running a node
==============

Deploying your node
-------------------

You deploy a node by running the ``gradle deployNodes`` task. This will output the node JAR to
``build/libs/corda.jar`` and several sample/standard node setups to ``build/nodes``. For now you can use the
``build/nodes/nodea`` configuration as a template.

Each node server by default must have a ``node.conf`` file in the current working directory. After first
execution of the node server there will be many other configuration and persistence files created in this
workspace directory. The directory can be overridden by the ``--base-directory=<workspace>`` command line argument.

.. note:: Outside of development environments do not store your node directories in the build folder.

.. warning:: Also note that the bootstrapping process of the ``corda.jar`` unpacks the Corda dependencies into a
   temporary folder. It is therefore suggested that the CAPSULE_CACHE_DIR environment variable be set before
   starting the process to control this location.

Starting your node
------------------

Now you have a node server with your app installed, you can run it by navigating to ``<node_dir>`` and running:

.. code-block:: shell

   Windows:   java -jar corda.jar
   UNIX:      ./corda.jar

The plugin should automatically be registered and the configuration file used.

.. warning:: If your working directory is not ``<node_dir>`` your plugins and configuration will not be used.

The configuration file and workspace paths can be overidden on the command line e.g.

``./corda.jar --config-file=test.conf --base-directory=/opt/r3corda/nodes/test``.

Otherwise the workspace folder for the node is the current working path.

Debugging your node
-------------------

To enable remote debugging of the corda process use a command line such as:

``java -Dcapsule.jvm.args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" -jar corda.jar``

This command line will start the debugger on port 5005 and pause the process awaiting debugger attachment.

Viewing persisted state of your node
------------------------------------

To make examining the persisted contract states of your node or the internal node database tables easier, and providing you are
using the default database configuration used for demos, you should be able to connect to the internal node database over
a JDBC connection at the URL that is output to the logs at node start up.  That URL will be of the form ``jdbc:h2:tcp://<host>:<port>/node``.

The user name and password for the login are as per the node data source configuration.

The name and column layout of the internal node tables is in a state of flux and should not be relied upon to remain static
at the present time, and should certainly be treated as read-only.

.. _CordaPluginRegistry: api/kotlin/corda/net.corda.core.node/-corda-plugin-registry/index.html
.. _PluginServiceHub: api/kotlin/corda/net.corda.core.node/-plugin-service-hub/index.html
.. _ServiceHub: api/kotlin/corda/net.corda.core.node/-service-hub/index.html

Building against Corda
----------------------

To publish to your local Maven repository (in ``~/.m2`` on Unix and ``%HOMEPATH%\.m2`` on Windows) run the following
in the root directory of the Corda code:

.. code-block:: shell

    ./gradlew install

This will publish corda-$version.jar, finance-$version.jar, core-$version.jar and node-$version.jar to the
group net.corda. You can now depend on these as you normally would a Maven dependency, using the group id
``net.corda``.

There are several Gradle plugins that reduce your build.gradle boilerplate and make development of CorDapps easier.
The available plugins are in the gradle-plugins directory of the Corda repository.

To install to your local Maven repository the plugins that CorDapp gradle files require, enter the ``gradle-plugins``
directory and then run ``../gradle install``. The plugins will now be installed to your local Maven repository.

Using Gradle plugins
~~~~~~~~~~~~~~~~~~~~

To use the plugins, if you are not already using the CorDapp template project, you must modify your build.gradle. Add
the following segments to the relevant part of your build.gradle.

.. code-block:: groovy

    buildscript {
        ext.corda_release_version = '<enter the corda version you build against here>'
        ext.corda_gradle_plugins_version = '<enter the gradle plugins version here>' // This is usually the same as corda_release_version.
        ... your buildscript ...

        repositories {
            ... other repositories ...
            mavenLocal()
        }

        dependencies {
            ... your dependencies ...
            classpath "net.corda.plugins:cordformation:$corda_gradle_plugins_version"
            classpath "net.corda.plugins:quasar-utils:$corda_gradle_plugins_version"
            classpath "net.corda.plugins:publish-utils:$corda_gradle_plugins_version"
        }
    }

    apply plugin: 'net.corda.plugins.cordformation'
    apply plugin: 'net.corda.plugins.quasar-utils'
    apply plugin: 'net.corda.plugins.publish-utils'

    repositories {
        mavenLocal()
        ... other repositories here ...
    }

    dependencies {
        compile "net.corda.core:$corda_release_version"
        compile "net.corda.finance:$corda_release_version"
        compile "net.corda.node:$corda_release_version"
        compile "net.corda.corda:$corda_release_version"
        ... other dependencies here ...
    }

    ... your tasks ...

    // Standard way to publish CorDapps to maven local with the maven-publish and publish-utils plugin.
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



Cordformation
~~~~~~~~~~~~~

Cordformation is the local node deployment system for CorDapps, the nodes generated are intended to be used for
experimenting, debugging, and testing node configurations and setups but not intended for production or testnet
deployment.

To use this gradle plugin you must add a new task that is of the type ``net.corda.plugins.Cordform`` to your
build.gradle and then configure the nodes you wish to deploy with the Node and nodes configuration DSL.
This DSL is specified in the `JavaDoc <api/javadoc/>`_. An example of this is in the CorDapp template and
below
is a three node example;

.. code-block:: text

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        directory "./build/nodes" // The output directory
        networkMap "CN=Controller,O=R3,OU=corda,L=London,C=GB" // The distinguished name of the node named here will be used as the networkMapService.address on all other nodes.
        node {
            name "CN=Controller,O=R3,OU=corda,L=London,C=GB"
            advertisedServices = [ "corda.notary.validating" ]
            p2pPort 10002
            rpcPort 10003
            webPort 10004
            h2Port 11002
            cordapps []
        }
        node {
            name "CN=NodeA,O=R3,OU=corda,L=London,C=GB"
            advertisedServices = []
            p2pPort 10005
            rpcPort 10006
            webPort 10007
            h2Port 11005
            cordapps []
        }
        node {
            name "CN=NodeB,O=R3,OU=corda,L=New York,C=US"
            advertisedServices = []
            p2pPort 10008
            rpcPort 10009
            webPort 10010
            h2Port 11008
            cordapps []
        }
    }

You can create more configurations with new tasks that extend Cordform.

New nodes can be added by simply adding another node block and giving it a different name, directory and ports. When you
run this task it will install the nodes to the directory specified and a script will be generated to run the nodes with
one command (``runnodes``). On MacOS X this script will run each node in a new terminal tab, and on Linux it will open
up a new XTerm for each node. On Windows the (``runnodes.bat``) script will run one node per window.

Other CorDapps can also be specified if they are already specified as classpath or compile dependencies in your
``build.gradle``.
