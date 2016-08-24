Creating a Cordapp
==================

A Cordapp is an application that runs on the Corda platform using Corda APIs and plugin system. Cordapps are self
contained in separate JARs from the Corda standalone JAR that are created and distributed.

Plugins
-------

.. note:: Currently plugins are only supported for JVM languages.

To create a plugin you must extend from `CordaPluginRegistry`_. The JavaDoc contains
specific details of the implementation, but you can extend the server in the following ways:

.. _CordaPluginRegistry: api/com.r3corda.core.node/-corda-plugin-registry/index.html

1. Required protocols: Specify which protocols will be whitelisted for use in your web APIs.
2. Service plugins: Register your services that run when Corda runs.
3. Web APIs: You may register your own endpoints under /api/ of the built-in web server.
4. Static web endpoints: You may register your own static serving directories for serving web content.

Services are used primarily for creating your own protocols, which will be the work horse of your Corda plugins. Web
APIs and serving directories will be for creating your own interface to Corda.

Standalone Nodes
----------------

To use a plugin you must also have a standalone node. To create a standalone node you run:

**Windows**::

    gradlew.bat createStandalone

**Other**::

    ./gradlew createStandalone

This will output the standalone to ``build/libs/r3prototyping-x.y-SNAPSHOT-capsule.jar`` and several sample/standard
standalone nodes to ``build/standalone``. For now you can use the ``build/standalone/nodea`` directory as a template or
example.

Each standalone node must contain a ``node.conf`` file in the same directory as the standalone JAR file. After first
execution of the standalone node there will be many other configuration and persistence files created in this directory.

.. warning:: Deleting your standalone node directory in any way, including ``gradle clean`` will delete all persistence.

.. note:: Outside of development environments do not store your standalone node in the build folder.

Installing Plugins
------------------

Once you have created your distribution JAR you can install it to a node by adding it to ``<node_dir>/plugins/``. In this
case the ``node_dir`` is the location where your standalone node's JAR and configuration file is.

.. note:: If the plugins directory does not exist you can create it manually.

Starting your Node
------------------

Now you have a standalone node with your Cordapp installed, you can run it by navigating to ``<node_dir>`` and running

    java -jar r3prototyping-x.y-SNAPSHOT-capsule.jar

The plugin should automatically be registered and the configuration file used.

.. warning:: If your working directory is not ``<node_dir>`` your plugins and configuration will not be used.

