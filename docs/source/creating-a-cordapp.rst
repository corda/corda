Creating a Cordapp
==================

A Cordapp is an application that runs on the Corda platform using the platform APIs and plugin system. They are self
contained in separate JARs from the node server JAR that are created and distributed.

App Plugins
-----------

.. note:: Currently apps are only supported for JVM languages.

To create an app plugin you must you must extend from `CordaPluginRegistry`_. The JavaDoc contains
specific details of the implementation, but you can extend the server in the following ways:

1. Required protocols: Specify which protocols will be whitelisted for use in your web APIs.
2. Service plugins: Register your :ref:`services`.
3. Web APIs: You may register your own endpoints under /api/ of the built-in web server.
4. Static web endpoints: You may register your own static serving directories for serving web content.

Services
--------

.. _services:

Services are classes which are constructed after the node has started. It is provided a `ServiceHubInternal`_ which
allows a richer API than the `ServiceHub`_ exposed to contracts. It enables adding protocols, registering
message handlers and more. The service does not run in a separate thread, so the only entry point to the service is during
construction, where message handlers should be registered and threads started.


Starting Nodes
--------------

To use an app you must also have a node server. To create a node server run the gradle installTemplateNodes task.

This will output the node JAR to ``build/libs/corda.jar`` and several sample/standard
node setups to ``build/nodes``. For now you can use the ``build/nodes/nodea`` configuration as a template.

Each node server must have a ``node.conf`` file in the same directory as the node JAR file. After first
execution of the node server there will be many other configuration and persistence files created in this directory.

.. note:: Outside of development environments do not store your node directories in the build folder.

Installing Apps
------------------

Once you have created your app JAR you can install it to a node by adding it to ``<node_dir>/plugins/``. In this
case the ``node_dir`` is the location where your node server's JAR and configuration file is.

.. note:: If the directory does not exist you can create it manually.

Starting your Node
------------------

Now you have a node server with your app installed, you can run it by navigating to ``<node_dir>`` and running

    java -jar corda.jar

The plugin should automatically be registered and the configuration file used.

.. warning:: If your working directory is not ``<node_dir>`` your plugins and configuration will not be used.

.. _CordaPluginRegistry: api/com.r3corda.core.node/-corda-plugin-registry/index.html
.. _ServiceHubInternal: api/com.r3corda.node.services.api/-service-hub-internal/index.html
.. _ServiceHub: api/com.r3corda.node.services.api/-service-hub/index.html