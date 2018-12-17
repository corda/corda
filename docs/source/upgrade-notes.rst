.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Upgrading a CorDapp to a new platform version
=============================================

These notes provide instructions for upgrading your CorDapps from previous versions of Corda to version |release|.

.. contents::
    :depth: 1

UNRELEASED
----------

FinalityFlow
^^^^^^^^^^^^

The previous ``FinalityFlow`` API is insecure. It requires a handler flow in the counterparty node which accepts any and
all signed transactions that are sent to it, without checks. It is **highly** recommended that existing CorDapps migrate
away to the new API.

As an example, let's take a very simple flow that finalises a transaction without the involvement of a counterpart flow:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/kotlin/FinalityFlowMigration.kt
        :language: kotlin
        :start-after: DOCSTART SimpleFlowUsingOldApi
        :end-before: DOCEND SimpleFlowUsingOldApi

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/FinalityFlowMigration.java
        :language: java
        :start-after: DOCSTART SimpleFlowUsingOldApi
        :end-before: DOCEND SimpleFlowUsingOldApi
        :dedent: 4

To use the new API, this flow needs to be annotated with ``InitiatingFlow`` and a ``FlowSession`` to the participant of the transaction must be
passed to ``FinalityFlow`` :

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/kotlin/FinalityFlowMigration.kt
        :language: kotlin
        :start-after: DOCSTART SimpleFlowUsingNewApi
        :end-before: DOCEND SimpleFlowUsingNewApi

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/FinalityFlowMigration.java
        :language: java
        :start-after: DOCSTART SimpleFlowUsingNewApi
        :end-before: DOCEND SimpleFlowUsingNewApi
        :dedent: 4

If there are more than one transaction participants then a session to each one must be initiated, excluding the local party
and the notary.

A responder flow has to be introduced, which will automatically run on the other participants' nodes, which will call ``ReceiveFinalityFlow``
to record the finalised transaction:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/kotlin/FinalityFlowMigration.kt
        :language: kotlin
        :start-after: DOCSTART SimpleNewResponderFlow
        :end-before: DOCEND SimpleNewResponderFlow

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/FinalityFlowMigration.java
        :language: java
        :start-after: DOCSTART SimpleNewResponderFlow
        :end-before: DOCEND SimpleNewResponderFlow
        :dedent: 4

For flows which are already initiating counterpart flows then it's a simple matter of using the existing flow session.
Note however, the new ``FinalityFlow`` is inlined and so the sequence of sends and receives between the two flows will
change and will be incompatible with your current flows. You can use the flow version API to write your flows in a
backwards compatible way.

Here's what an upgraded initiating flow may look like:

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/kotlin/FinalityFlowMigration.kt
        :language: kotlin
        :start-after: DOCSTART ExistingInitiatingFlow
        :end-before: DOCEND ExistingInitiatingFlow

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/FinalityFlowMigration.java
        :language: java
        :start-after: DOCSTART ExistingInitiatingFlow
        :end-before: DOCEND ExistingInitiatingFlow
        :dedent: 4

For the responder flow, insert a call to ``ReceiveFinalityFlow`` at the location where it's expecting to receive the
finalised transaction. If the initiator is written in a backwards compatible way then so must the responder.

.. container:: codeset

    .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/kotlin/FinalityFlowMigration.kt
        :language: kotlin
        :start-after: DOCSTART ExistingResponderFlow
        :end-before: DOCEND ExistingResponderFlow
        :dedent: 8

    .. literalinclude:: example-code/src/main/java/net/corda/docs/java/FinalityFlowMigration.java
        :language: java
        :start-after: DOCSTART ExistingResponderFlow
        :end-before: DOCEND ExistingResponderFlow
        :dedent: 12

The responder flow may be waiting for the finalised transaction to appear in the local node's vault using ``waitForLedgerCommit``.
This is no longer necessary with ``ReceiveFinalityFlow`` and the call to ``waitForLedgerCommit`` can be removed.

Upgrading from Corda Open Source
--------------------------------

A prerequisite to upgrade to |release| is to ensure your CorDapp is upgraded to Open Source Corda 3.x.
Please follow the instructions in `Corda V3.1 upgrade notes <https://docs.corda.net/releases/release-V3.1/upgrade-notes.html#v3-0-to-v3-1>`_
and `Corda V3.0 upgrade notes <https://docs.corda.net/releases/release-V3.0/upgrade-notes.html#v2-0-to-v3-0>`_ section to complete this initial step.

.. note::
    There is no requirement to upgrade your CorDapp to Corda Enterprise in order to run it on Corda Enterprise. If
    you wish your CorDapp to be compatible with nodes running Open Source, then compiling against Open Source Corda V3.x
    will suffice.

Upgrading to |release| from Open Source 3.x requires updating build file properties. For Gradle:

.. sourcecode:: shell

    ext.corda_release_distribution = 'com.r3.corda'
    ext.corda_release_version = '3.1'
    ext.corda_gradle_plugins_version = '4.0.25'

and specifying an additional repository entry to point to the location of the Corda Enterprise distribution. As an example:

.. sourcecode:: shell

    repositories {
        maven {
            credentials {
                username "username"
                password "XXXXX"
            }
            url 'https://artifactory.mycompany.com/artifactory/corda-enterprise'
        }
    }

.. note:: |release| binaries are not available in a public repository. In order to make the dependencies available for development, either create a mirror repository and upload them there, or add them to the local Maven repository.

.. note:: While the Corda Gradle Plugins need no changes apart from the version, ensure that Corda Enterprise dependencies are referenced with the right distribution. As an example:

   .. sourcecode:: shell

       cordaCompile "net.corda:corda-core:$corda_release_version"

   becomes:

   .. sourcecode:: shell

       cordaCompile "$corda_release_distribution:corda-core:$corda_release_version"


|release| uses Kotlin API and language version 1.2. The specifics are

.. sourcecode:: shell

    ext.kotlin_version = '1.2.50'

.. note:: Explicit overriding of properties ``ext.quasar_group`` and ``ext.quasar_version`` is not necessary anymore
   for CorDapps depending on the ``quasar-utils`` plugin. You can remove these two lines from which ever file.

Certificate Revocation List (CRL) support
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The newly added feature of certificate revocation (see :doc:`certificate-revocation`) introduces a few changes to the node configuration.
In the configuration file it is required to explicitly specify how strictly the node should apply the CRL check. For that purpose the ``crlCheckSoftFail``
parameter is now expected to be set explicitly in the node's SSL configuration.
Setting the ``crlCheckSoftFail`` to true, relaxes the CRL checking policy. In this mode, the SSL communication
will fail only when the certificate revocation status can be checked and the certificate is revoked. Otherwise it will succeed.
If ``crlCheckSoftFail`` is false, then an SSL failure can occur if the certificate revocation status cannot be checked (e.g. due to a network failure), as well as when
a certificate is revoked.

Older versions of Corda do not have CRL distribution points embedded in the SSL certificates.
As such, in order to be able to reuse node and SSL certificates generated in those versions of Corda, the ``crlCheckSoftFail`` needs
to be set to true.

.. note::
    Mitigation of this issue and thus being able to use the ``strict`` mode (i.e. with ``crlCheckSoftFail = false``)
    of the CRL checking with the certificates generated in previous versions of Corda is going to be added in the near future.

Upgrading from Corda Enterprise 3.0 Developer Preview 3.0
---------------------------------------------------------
A limited distribution preview of |release| was made available to a small number of users. If your app uses this version, here are some specific notes on how to upgrade to the Generally Available release:

* Update versions in your build files, for Gradle, as an example:

  .. sourcecode:: shell

      ext.corda_release_version = '3.1'
      ext.corda_gradle_plugins_version = '4.0.25'
      ext.kotlin_version = '1.2.50'

  .. note:: Explicit overriding of properties ``ext.quasar_group`` and ``ext.quasar_version`` is not necessary anymore for CorDapps depending on the ``quasar-utils`` plugin. You can remove these two lines from which ever file.

* For CorDapps depending on the ``cordapp-plugin``, version ``4.0.25`` allows specifying distribution information. As an example:

  .. sourcecode:: groovy

      cordapp {
        info {
          name "My CorDapp"
          vendor "My Company"
          version "1.0.1"
        }
      }

  .. note:: Properties ``name`` and ``version`` of a CorDapp's distribution information are derived automatically by
     the ``cordapp-plugin`` if not provided. The ``vendor`` property should be provided explicitly. A warning is raised
     by Corda Enterprise nodes for CorDapps that do not specify the ``vendor`` property.

Certificate Revocation List (CRL) support
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The newly added feature of certificate revocation (see :doc:`certificate-revocation`) introduces a few changes to the node configuration.
In the configuration file it is required to explicitly specify how strictly the node should apply the CRL check. For that purpose the ``crlCheckSoftFail``
parameter is now expected to be set explicitly in the node's SSL configuration.
Setting the ``crlCheckSoftFail`` to true, relaxes the CRL checking policy. In this mode, the SSL communication
will fail only when the certificate revocation status can be checked and the certificate is revoked. Otherwise it will succeed.
If ``crlCheckSoftFail`` is false, then an SSL failure can occur if the certificate revocation status cannot be checked (e.g. due to a network failure), as well as when
a certificate is revoked.

Older versions of Corda do not have CRL distribution points embedded in the SSL certificates.
As such, in order to be able to reuse node and SSL certificates generated in those versions of Corda, the ``crlCheckSoftFail`` needs
to be set to true.

.. note::
    Mitigation of this issue and thus being able to use the ``strict`` mode (i.e. with ``crlCheckSoftFail = false``)
    of the CRL checking with the certificates generated in previous versions of Corda is going to be added in the near future.

Upgrading from Corda Enterprise 3
---------------------------------

API Changes
+++++++++++

* MockNetwork: ``MockNodeParameters`` and functions creating it no longer use a lambda expecting a ``NodeConfiguration``
  object. Use a ``MockNetworkConfigOverrides`` object instead.
