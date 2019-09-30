Getting set up for CorDapp development
======================================

There are four pieces of required software for CorDapp development: the Java 8 JDK, IntelliJ IDEA, Git, and Gradle 4.10.

1. Install the Java 8 JDK, version |java_version|. We have tested using the following Java builds:

  - `Oracle JDK <https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html>`_
  - `Amazon Corretto <https://aws.amazon.com/corretto/>`_
  - `Red Hat's OpenJDK <https://developers.redhat.com/products/openjdk/overview/>`_
  - `Zulu's OpenJDK <https://www.azul.com/>`_

  Please note: OpenJDK builds often exclude JavaFX, which is required by the Corda GUI tools. Corda supports only Java 8.

  If you are using Windows: Add Java to the PATH environment variable by following the instructions in the `Oracle documentation <https://docs.oracle.com/javase/7/docs/webnotes/install/windows/jdk-installation-windows.html#path>`_.

2. Install `IntelliJ IDEA Community Edition <https://www.jetbrains.com/idea/>`_. Corda supports IntelliJ IDEA versions **2017.x**, **2018.x**, and **2019.x**; and Kotlin plugin version |kotlin_version|.

  To install IntelliJ IDEA in a Ubuntu environment, navigate to the `Jetbrains IntelliJ snap package <https://snapcraft.io/intellij-idea-community>`_.

3. Install `git <https://git-scm.com/>`_.

4. Install `Gradle version 4.10 <https://gradle.org/install/>`_. If you are using a supported Corda sample, the included ``gradlew`` script should install Gradle automatically.

  Please note: Corda requires Gradle version 4.10, and does not support any other version of Gradle.

Next steps
----------

First, run the :doc:`example CorDapp <tutorial-cordapp>`.

Next, read through the :doc:`Corda Key Concepts <key-concepts>` to understand how Corda works.

By then, you'll be ready to start writing your own CorDapps. You may want to refer to the
:doc:`API documentation <corda-api>`, the :doc:`flow cookbook <flow-cookbook>` and the
`samples <https://www.corda.net/samples/>`_ along the way.

If you encounter any issues, please ask on `Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ or via `our Slack channels <http://slack.corda.net/>`_.
