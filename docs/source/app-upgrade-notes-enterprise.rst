.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Upgrading CorDapps to |release|
===============================

Upgrading from Open Source
--------------------------

Running on |release|
~~~~~~~~~~~~~~~~~~~~

A prerequisite to upgrade to |release| is to ensure your CorDapp is upgraded to Open Source Corda 4.x.
Please follow the instructions in :doc:`app-upgrade-notes` section to complete this initial step.

There is no requirement to re-compile your CorDapp to Corda Enterprise in order to run it on Corda Enterprise. If you wish your CorDapp to
be compatible with nodes running Open Source, then compiling against Open Source Corda V4.x will suffice.
However, if you wish to leverage Corda Enterprise specific features, such as 3rd party commercial database support, and do not intend
for your CorDapp to run in an open source production environment, then please follow the instructions :ref:`here <recompiling_for_enterprise>`

.. note:: Corda open source and Enterprise Public APIs are currently identical but this may change in future releases of Corda Enterprise.
   Please read :doc:`version-compatibility` guarantees.

.. _recompiling_for_enterprise:

Re-compiling for |release|
~~~~~~~~~~~~~~~~~~~~~~~~~~

Re-compiling your CorDapp requires updating its associated Gradle build file as follows:

.. sourcecode:: shell

    ext.corda_release_distribution = 'com.r3.corda'
    ext.corda_release_version = '|corda_version|'
    ext.corda_gradle_plugins_version = '|gradle_plugins_version|'
    ext.kotlin_version = '|kotlin_version|'
    ext.quasar_version = '|quasar_version|'

and specifying an additional repository entry to point to the location of the Corda Enterprise distribution and Corda dependencies. As an example:

If your project is based on one of the official cordapp templates, it is likely you have a ``lib/quasar.jar`` checked in.  It is worth noting
that you only use this if you use the JUnit runner in IntelliJ.  In the latest release of the cordapp templates, this directory has
been removed.

.. sourcecode:: shell

    repositories {
        // Example for Corda Enterprise
        maven {
            credentials {
                username "username"
                password "XXXXX"
            }
            url 'https://artifactory.mycompany.com/artifactory/corda-enterprise'
        }

        // Corda dependencies for the patched Quasar version
        maven { url "https://software.r3.com/artifactory/corda-dependencies" } // access to the patched Quasar version
    }

* Upgrade your ``quasar.jar`` to the version consistent with your Corda version
* Delete your ``lib`` directory and switch to using the Gradle test runner

While the Corda Gradle Plugins need no changes apart from the version, ensure that Corda Enterprise dependencies are referenced with the right distribution. As an example:

.. sourcecode:: shell

   cordaCompile "net.corda:corda-core:$corda_release_version"

becomes:

.. sourcecode:: shell

   cordaCompile "$corda_release_distribution:corda-core:$corda_release_version"

.. note:: |release| binaries are not available in a public repository. In order to make the dependencies available for development, either
   create a mirror repository and upload them there, or add them to the local Maven repository.

   Please consult your R3 support contact to request a copy of the Corda Enterprise Developer Pack (this contains a Maven repository mirror
   of all Corda Enterprise artifacts and their dependencies).

.. warning:: In Corda 4 the original Finance CorDapp was split into two CorDapps: Contracts and Workflows, both of which are signed JARs.
   To ensure there is only one unique hashed version of the Finance Contracts JAR (recall, the hash of a Contract JAR undergoes strict
   security checking upon transaction resolution) we only publish a single instance of the Finance Contracts JAR (from the open source repository).
   Please ensure any CorDapps that depend on Finance Contract JAR reference this open source dependency as follows:

   .. sourcecode:: shell

      cordapp "$os_corda_release_distribution:corda-finance-contracts:$os_corda_release_version"

   where

   .. sourcecode:: shell

      ext.os_corda_release_distribution = 'net.corda'
      ext.os_corda_release_version = '4.0'

Upgrading from Enterprise 3.x
-----------------------------

Firstly, please update all your CorDapp project dependencies as described in :ref:`re-compiling for Corda Enterprise <recompiling_for_enterprise>`.

.. include link to "Advanced CorDapp Concepts" when PR is completed: https://github.com/corda/corda/pull/4798

Adjust your CorDapp source code as necessary to take into account the following upgrade constraints:

.. _cordapps_external_dependencies:

1. Contract CorDapps JARs with external dependencies.

   Refers to a Contract CorDapp JAR that depends on one or more classes from another independently classloaded JAR (which may be another
   Contract CorDapp JAR or a 3rd party library).

   Prior to Corda 4 the node used a single applications classloader for loading all CorDapps and 3rd party JARs (including node dependencies
   themselves). Corda 4 introduces an isolated classloader, the **attachments classloader**, for the sole purpose of transaction verification.
   Upon transaction verification, this classloader will attempt to resolve Contract CorDapp attachments from its internal attachments storage
   (this holds all versions of all Contract CorDapps loaded by the node from its /cordapps directory or manually uploaded using the RPC
   ``uploadAttachment`` secure API). However, Contract CorDapps **do not currently have a mechanism to explicitly specify dependencies on
   external classes from other JARs** so the attachments classloader has no means of knowing what other dependent JARs to classload.

   The implications of this are as follows:

   - Contract CorDapps should be packaged as **fat JARs** in Corda 4: they should be self-contained and include all classes required by the attachments classloader.

   - Contract states created pre-Corda 4 using CorDapps that, purposefully or inadvertently had external dependencies on other JARs, would
     seamlessly verify in transactions a pre-Corda 4 node due to the lack of classloader isolation of attachment JARs (eg. all classloaded JARs are visible to
     all other classloaded JARs). To maintain backwards compatibility, Corda 4 introduces a "classloader fallback mechanism" which will attempt to
     resolve and classload any referenced classes not found by the attachments classloader by scanning the applications classloader.

.. warning:: The "classloader fallback mechanism" will be removed in a future version of Corda in favour of declarative dependency management,
   whereby a Contract CorDapp will declare any dependencies on external classes in its own JAR metadata (similar to module ``requires``
   declarations in `Java 9 modules <https://www.oracle.com/corporate/features/understanding-java-9-modules.html>`_).

Example
~~~~~~~
CorDapps built using the new `Token SDK <https://github.com/corda/token-sdk>`_ fall into this category, specifically any CorDapp that
extends the Token SDK ``EvolvableTokenType`` which is an abstract class that indirectly implements ``Contract``. The ``build.gradle``
file of the 3rd party Contract CorDapp should specify inclusive Token SDK CorDapp dependencies as follows:

.. sourcecode:: groovy

    ext.tokens_sdk_release_group = 'com.r3.tokens-sdk'
    ext.tokens_sdk_version = '1.0-SNAPSHOT'
    ...

    dependencies {
        ...
        // Token SDK dependencies.
        compile "$tokens_sdk_release_group:contract:$tokens_sdk_version"
        compile "$tokens_sdk_release_group:money:$tokens_sdk_version"
        ...
    }

2. Workflow CorDapps.

   Please follow the instructions listed in step 5 of :ref:`Upgrading apps to Corda 4 <cordapp_upgrade_finality_flow_ref>`.