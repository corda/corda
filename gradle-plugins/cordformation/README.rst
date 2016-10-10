Cordformation
=============

Plugin Maven Name::

    cordformation

Cordformation is the local node deployment system for Cordapps, the nodes generated are intended to be used for
experimenting, debugging, and testing node configurations and setups but not intended for production or testnet
deployment.

To use this plugin you must add a new task that is of the type `com.r3corda.plugins.Cordform` and then configure
the nodes you wish to deploy with the Node and nodes configuration DSL. This DSL is specified in the JavaDoc but
an example of this is in the template-cordapp and below is a three node example;

.. code-block:: text

    task deployNodes(type: com.r3corda.plugins.Cordform, dependsOn: ['build']) {
        directory "./build/nodes" // The output directory
        networkMap "Notary" // The artemis address of the node named here will be used as the networkMapAddress on all other nodes.
        node {
            name "Notary"
            dirName "notary"
            nearestCity "London"
            notary true // Sets this node to be a notary
            advertisedServices = []
            artemisPort 12345
            webPort 12346
            cordapps = []
        }
        node {
            name "NodeA"
            dirName "nodea"
            nearestCity "London"
            advertisedServices = []
            artemisPort 31337
            webPort 31339
            cordapps = []
        }
        node {
            name "NodeB"
            dirName "nodeb"
            nearestCity "New York"
            advertisedServices = []
            artemisPort 31338
            webPort 31340
            cordapps = []
        }
    }

You can create more configurations with new tasks that extend Cordform.

New nodes can be added by simply adding another node block and giving it a different name, directory and ports. When you
run this task it will install the nodes to the directory specified and a script will be generated (for UNIX users only
at present) to run the nodes with one command.

Other cordapps can also be specified if they are already specified as classpath or compile dependencies in your
build.gradle.