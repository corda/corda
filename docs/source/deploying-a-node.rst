Deploying a node
================

Building nodes using Gradle
---------------------------
Nodes are usually built using a Gradle task. The cannonical Gradle file for building nodes is the one used by the
CorDapp template. Both a `Java version <https://github.com/corda/cordapp-template-java/blob/master/build.gradle>`_ and
a `Kotlin version <https://github.com/corda/cordapp-template-kotlin/blob/master/build.gradle>`_ are available.

The buildscript
~~~~~~~~~~~~~~~
The buildscript is always located at the top of the build file. Among other things, this section determines which
version of Corda and the Corda gradle plugins are used to build the nodes and their CorDapps:

.. sourcecode:: groovy

    ext.corda_release_version = '0.14-SNAPSHOT'
    ext.corda_gradle_plugins_version = '0.14.2'

Release versions suffixed by ``-SNAPSHOT`` are based on the unstable Master branch
(see :doc:`building-against-master`). You should generally use the latest Milestone release instead.

``corda_gradle_plugins_versions`` are given in the form ``major.minor.patch``. You should use the same ``major`` and
``minor`` versions as the Corda version you are using, and the latest ``patch`` version. A list of all the available
versions can be found here: https://bintray.com/r3/corda/cordformation.

Project dependencies
~~~~~~~~~~~~~~~~~~~~
If your CorDapps have any additional external dependencies, they should be added to the ``dependencies`` section:`

.. sourcecode:: groovy

  dependencies {

      ...

      // Cordapp dependencies
      // Specify your cordapp's dependencies below, including dependent cordapps
  }

For further information about managing dependencies, see
`the Gradle docs <https://docs.gradle.org/current/userguide/dependency_management.html>`_.

Cordform and deployNodes
~~~~~~~~~~~~~~~~~~~~~~~~
Cordform is the local node deployment system for CorDapps. The nodes generated are intended for experimenting,
debugging, and testing node configurations, but not for production or testnet deployment.

Here is an example Gradle task called ``deployNodes`` that uses the Cordform plugin to deploy three nodes, plus a
notary/network map node:

.. sourcecode:: groovy

    task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
        directory "./build/nodes"
        networkMap "CN=Controller,O=R3,OU=corda,L=London,C=UK"
        node {
            name "CN=Controller,O=R3,OU=corda,L=London,C=UK"
            advertisedServices = ["corda.notary.validating"]
            p2pPort 10002
            rpcPort 10003
            webPort 10004
            cordapps = []
        }
        node {
            name "CN=NodeA,O=NodeA,L=London,C=UK"
            advertisedServices = []
            p2pPort 10005
            rpcPort 10006
            webPort 10007
            cordapps = []
            rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
        }
        node {
            name "CN=NodeB,O=NodeB,L=New York,C=US"
            advertisedServices = []
            p2pPort 10008
            rpcPort 10009
            webPort 10010
            cordapps = []
            rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
        }
        node {
            name "CN=NodeC,O=NodeC,L=Paris,C=FR"
            advertisedServices = []
            p2pPort 10011
            rpcPort 10012
            webPort 10013
            cordapps = []
            rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
        }
    }

You can extend ``deployNodes`` to generate any number of nodes you like. The only requirement is that you must specify
one node as running the network map service.

.. warning:: When adding nodes, make sure that there are no port clashes!

Any CorDapps defined in the project's source folders are also automatically registered with all the nodes defined in
``deployNodes``, even if the CorDapps are not listed in each node's ``cordapps`` entry.

Deploying your nodes
--------------------
You deploy a set of nodes by running your ``build.gradle`` file's Cordform task. For example, if we were using the
standard ``deployNodes`` task defined above, we'd create our nodes by running the following commands in a terminal
window from the root of the project:

* Unix/Mac OSX: ``./gradlew deployNodes``
* Windows: ``gradlew.bat deployNodes``

After the build process has finished, you will find the newly-built nodes under ``kotlin-source/build/nodes``. There
will be one folder generated for each node you built, plus a ``runnodes`` shell script (or batch file on Windows) to
run all the nodes at once. Each node in the ``nodes`` folder has the following structure:

.. sourcecode:: none

    . nodeName
    ├── corda.jar       // The Corda runtime
    ├── node.conf       // The node's configuration
    └── plugins         // Any installed CorDapps

.. note:: Outside of development environments, do not store your node directories in the build folder.

If you make any changes to your ``deployNodes`` task, you will need to re-run the task to see the changes take effect.