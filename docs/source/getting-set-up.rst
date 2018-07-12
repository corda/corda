Getting set up
==============

Software requirements
---------------------
Corda uses industry-standard tools:

* **Oracle JDK 8 JVM** - minimum supported version **8u171**
* **IntelliJ IDEA** - supported versions **2017.x** and **2018.x** (with Kotlin plugin version |kotlin_version|)
* **Git**

We also use Gradle and Kotlin, but you do not need to install them. A standalone Gradle wrapper is provided, and it 
will download the correct version of Kotlin.

Please note:

* Corda runs in a JVM. JVM implementations other than Oracle JDK 8 are not actively supported. However, if you do
  choose to use OpenJDK, you will also need to install OpenJFX

* Applications on Corda (CorDapps) can be written in any language targeting the JVM. However, Corda itself and most of
  the samples are written in Kotlin. Kotlin is an
  `official Android language <https://developer.android.com/kotlin/index.html>`_, and you can read more about why
  Kotlin is a strong successor to Java
  `here <https://medium.com/@octskyward/why-kotlin-is-my-next-programming-language-c25c001e26e3>`_. If you're
  unfamiliar with Kotlin, there is an official
  `getting started guide <https://kotlinlang.org/docs/tutorials/>`_, and a series of
  `Kotlin Koans <https://kotlinlang.org/docs/tutorials/koans.html>`_.

* IntelliJ IDEA is recommended due to the strength of its Kotlin integration

Following these software recommendations will minimize the number of errors you encounter, and make it easier for
others to provide support. However, if you do use other tools, we'd be interested to hear about any issues that arise.

Set-up instructions
-------------------
The instructions below will allow you to set up a Corda development environment and run a basic CorDapp. If you have
any issues, please consult the :doc:`troubleshooting` page, or reach out on `Slack <http://slack.corda.net/>`_,
`Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ or the `forums <https://discourse.corda.net/>`_.

The set-up instructions are available for the following platforms:

* :ref:`windows-label` (or `in video form <https://vimeo.com/217462250>`__)

* :ref:`mac-label` (or `in video form <https://vimeo.com/217462230>`__)

.. _windows-label:

Windows
-------

.. warning:: If you are using a Mac machine, please follow the :ref:`mac-label` instructions instead.

Java
^^^^
1. Visit http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
2. Scroll down to "Java SE Development Kit 8uXXX" (where "XXX" is the latest minor version number)
3. Toggle "Accept License Agreement"
4. Click the download link for jdk-8uXXX-windows-x64.exe (where "XXX" is the latest minor version number)
5. Download and run the executable to install Java (use the default settings)
6. Add Java to the PATH environment variable by following the instructions at https://docs.oracle.com/javase/7/docs/webnotes/install/windows/jdk-installation-windows.html#path
7. Open a new command prompt and run ``java -version`` to test that Java is installed correctly

Git
^^^
1. Visit https://git-scm.com/download/win
2. Click the "64-bit Git for Windows Setup" download link.
3. Download and run the executable to install Git (use the default settings)
4. Open a new command prompt and type ``git --version`` to test that git is installed correctly

IntelliJ
^^^^^^^^
1. Visit https://www.jetbrains.com/idea/download/download-thanks.html?code=IIC
2. Download and run the executable to install IntelliJ Community Edition (use the default settings)
3. Ensure the Kotlin plugin in Intellij is updated to version |kotlin_version|

.. _mac-label:

Mac
---

.. warning:: If you are using a Windows machine, please follow the :ref:`windows-label` instructions instead.

Java
^^^^
1. Visit http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
2. Scroll down to "Java SE Development Kit 8uXXX" (where "XXX" is the latest minor version number)
3. Toggle "Accept License Agreement"
4. Click the download link for jdk-8uXXX-macosx-x64.dmg (where "XXX" is the latest minor version number)
5. Download and run the executable to install Java (use the default settings)
6. Open a new terminal window and run ``java -version`` to test that Java is installed correctly

IntelliJ
^^^^^^^^
1. Visit https://www.jetbrains.com/idea/download/download-thanks.html?platform=mac&code=IIC
2. Download and run the executable to install IntelliJ Community Edition (use the default settings)
3. Ensure the Kotlin plugin in Intellij is updated to version |kotlin_version|

Next steps
----------
First, run the :doc:`example CorDapp <tutorial-cordapp>`.

Next, read through the :doc:`Corda Key Concepts <key-concepts>` to understand how Corda works.

By then, you'll be ready to start writing your own CorDapps. Learn how to do this in the
:doc:`Hello, World tutorial <hello-world-introduction>`. You may want to refer to the
:doc:`API documentation <corda-api>`, the :doc:`flow cookbook <flow-cookbook>` and the
`samples <https://www.corda.net/samples/>`_ along the way.

If you encounter any issues, please ask on `Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ or via
`our Slack channels <http://slack.corda.net/>`_.
