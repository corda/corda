Integrating Cordapps
====================

Cordapps run on the Corda platform and integrate with it and each other. To learn more about the basics of a Cordapp
please read :doc:`cordapp-overview`. To learn about writing a Cordapp as a developer please read :doc:`writing-cordapps`.

This article will specifically deal with build systems.

Cordapp JAR Format
------------------

The first step to integrating a Cordapp with Corda is to ensure it is in the correct format. The correct format of a JAR
is a semi-fat JAR that contains all of its own dependencies _except_ the Corda core libraries and other Cordapps.

For example if your Cordapp depends on ``corda-core``, ``your-other-cordapp`` and ``apache-commons`` then the Cordapp
JAR will contain all classes and resources from the ``apache-commons`` JAR and its dependencies and __nothing__ from the
other two JARs.

..note:: The rest of this tutorial assumes you are using ``gradle``, the ``cordformation`` plugin and have forked from
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

..warning:: Never include ``corda-test-utils`` as a ``compile`` or ``corda`` dependency.

..note:: The ``cordformation`` plugin adds ``corda`` as a new configuration that ``compile`` extends from, and ``cordaRuntime``
         which ``runtime`` extends from. There is none for the test targets because these targets are only relevant for building the
         JAR.

Building against Cordapps
-------------------------

To build against a Cordapp you must add it as a ``cordapp`` dependency to your ``build.gradle``.

Installing CorDapps
-------------------

Your CorDapp JAR must be added it to a node's ``<node_dir>/plugins/`` folder (where ``node_dir`` is the folder in which the
node's JAR and configuration files are stored).

At runtime, nodes will load any plugins present in their ``plugins`` folder.

.. note:: Building nodes using the gradle ``deployNodes`` task will place the CorDapp JAR into each node's ``plugins``
folder automatically.
