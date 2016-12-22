CorDapps Background
===================

A Cordapp is an application that runs on the Corda platform using the platform APIs and plugin system. They are self
contained in separate JARs from the node server JAR that are created and distributed.

App plugins
-----------

.. note:: Currently apps are only supported for JVM languages.

To create an app plugin you must you must extend from `CordaPluginRegistry`_. The JavaDoc contains
specific details of the implementation, but you can extend the server in the following ways:

1. Required flows: Specify which flows will be whitelisted for use in your web APIs.
2. Service plugins: Register your services (see below).
3. Web APIs: You may register your own endpoints under /api/ of the built-in web server.
4. Static web endpoints: You may register your own static serving directories for serving web content.
5. Registering your additional classes used in RPC.

Services
--------

Services are classes which are constructed after the node has started. It is provided a `PluginServiceHub`_ which
allows a richer API than the `ServiceHub`_ exposed to contracts. It enables adding flows, registering
message handlers and more. The service does not run in a separate thread, so the only entry point to the service is during
construction, where message handlers should be registered and threads started.


Starting nodes
--------------

To use an app you must also have a node server. To create a node server run the ``gradle deployNodes`` task.

This will output the node JAR to ``build/libs/corda.jar`` and several sample/standard
node setups to ``build/nodes``. For now you can use the ``build/nodes/nodea`` configuration as a template.

Each node server by default must have a ``node.conf`` file in the current working directory. After first
execution of the node server there will be many other configuration and persistence files created in a node
workspace directory. This is specified as the basedir property of the node.conf file, or else can be overidden
using ``--base-directory=<workspace>``.

.. note:: Outside of development environments do not store your node directories in the build folder.

.. warning:: Also note that the bootstrapping process of the ``corda.jar`` unpacks the Corda dependencies into a
   temporary folder. It is therefore suggested that the CAPSULE_CACHE_DIR environment variable be set before
   starting the process to control this location.

Installing apps
---------------

Once you have created your app JAR you can install it to a node by adding it to ``<node_dir>/plugins/``. In this
case the ``node_dir`` is the location where your node server's JAR and configuration file is.

.. note:: If the directory does not exist you can create it manually.

Starting your node
------------------

Now you have a node server with your app installed, you can run it by navigating to ``<node_dir>`` and running

    java -jar corda.jar

The plugin should automatically be registered and the configuration file used.

.. warning:: If your working directory is not ``<node_dir>`` your plugins and configuration will not be used.

The configuration file and workspace paths can be overidden on the command line e.g.

``java -jar corda.jar --config-file=test.conf --base-directory=/opt/r3corda/nodes/test``.

Otherwise the workspace folder for the node is created based upon the ``basedir`` property in the ``node.conf`` file and if this is relative it is applied relative to the current working path.

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

.. _CordaPluginRegistry: api/net.corda.core.node/-corda-plugin-registry/index.html
.. _PluginServiceHub: api/net.corda.core.node/-plugin-service-hub/index.html
.. _ServiceHub: api/net.corda.core.node/-service-hub/index.html

Building against Corda
----------------------

.. warning:: This feature is subject to rapid change

Corda now supports publishing to Maven local to build against it. To publish to Maven local run the following in the
root directory of Corda

.. code-block:: shell

    ./gradlew install

This will publish corda-$version.jar, finance-$version.jar, core-$version.jar and node-$version.jar to the
group net.corda. You can now depend on these as you normally would a Maven dependency.

Gradle plugins for CorDapps
===========================

There are several Gradle plugins that reduce your build.gradle boilerplate and make development of Cordapps easier.
The available plugins are in the gradle-plugins directory of the Corda repository.

Building Gradle plugins
-----------------------

To install to your local Maven repository the plugins that Cordapp gradle files require, run the following from the
root of the Corda project:

.. code-block:: text

    ./gradlew install

The plugins will now be installed to your local Maven repository in ~/.m2 on Unix and %HOMEPATH%\.m2 on Windows.

Using Gradle plugins
--------------------

To use the plugins, if you are not already using the Cordapp template project, you must modify your build.gradle. Add
the following segments to the relevant part of your build.gradle.

Template build.gradle
---------------------

To build against Corda and the plugins that cordapps use, update your build.gradle to contain the following:

.. code-block:: groovy

    buildscript {
        ext.corda_version = '<enter the corda version you build against here>'
        ext.corda_gradle_plugins_version = '<enter the gradle plugins version here>' // This is usually the same as corda_version.
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
        compile "net.corda.core:$corda_version"
        compile "net.corda.finance:$corda_version"
        compile "net.corda.node:$corda_version"
        compile "net.corda.corda:$corda_version"
        ... other dependencies here ...
    }

    ... your tasks ...

    // Standard way to publish Cordapps to maven local with the maven-publish and publish-utils plugin.
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
-------------

Cordformation is the local node deployment system for Cordapps, the nodes generated are intended to be used for
experimenting, debugging, and testing node configurations and setups but not intended for production or testnet
deployment.

To use this gradle plugin you must add a new task that is of the type ``net.corda.plugins.Cordform`` to your
build.gradle and then configure the nodes you wish to deploy with the Node and nodes configuration DSL.
This DSL is specified in the `JavaDoc <api/index.html>`_. An example of this is in the template-cordapp and below
is a three node example;

.. code-block:: text

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['build']) {
        directory "./build/nodes" // The output directory
        networkMap "Controller" // The artemis address of the node named here will be used as the networkMapService.address on all other nodes.
        node {
            name "Controller"
            dirName "controller"
            nearestCity "London"
            advertisedServices = [ "corda.notary.validating" ]
            artemisPort 12345
            webPort 12346
            cordapps []
        }
        node {
            name "NodeA"
            dirName "nodea"
            nearestCity "London"
            advertisedServices = []
            artemisPort 31337
            webPort 31339
            cordapps []
        }
        node {
            name "NodeB"
            dirName "nodeb"
            nearestCity "New York"
            advertisedServices = []
            artemisPort 31338
            webPort 31340
            cordapps []
        }
    }

You can create more configurations with new tasks that extend Cordform.

New nodes can be added by simply adding another node block and giving it a different name, directory and ports. When you
run this task it will install the nodes to the directory specified and a script will be generated (for UNIX users only
at present) to run the nodes with one command (``runnodes``). On MacOS X this script will run each node in a new
terminal tab, and on Linux it will open up a new XTerm for each node. On Windows the (``runnodes.bat``) script will run
one node per window.

Other cordapps can also be specified if they are already specified as classpath or compile dependencies in your
``build.gradle``.
