Getting set up for CorDapp development
======================================

Software requirements
---------------------

Corda uses industry-standard tools:

* **Java 8 JVM** - we require at least version |java_version|, but do not currently support Java 9 or higher.

  We have tested with the following builds:

  * `Oracle JDK <https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html>`_

  * `Amazon Corretto <https://aws.amazon.com/corretto/>`_

  * `Red Hat's OpenJDK <https://developers.redhat.com/products/openjdk/overview/>`_

  * `Zulu's OpenJDK <https://www.azul.com/>`_

  Please note that OpenJDK builds usually exclude JavaFX, which our GUI tools require.

* **IntelliJ IDEA** - supported versions **2017.x**, **2018.x** and **2019.x** (with Kotlin plugin version |kotlin_version|)
* **Gradle** - we use 4.10 and the ``gradlew`` script in the project / samples directories will download it for you.

Please note:

* Applications on Corda (CorDapps) can be written in any language targeting the JVM. However, Corda itself and most of
  the samples are written in Kotlin. Kotlin is an
  `official Android language <https://developer.android.com/kotlin/index.html>`_, and you can read more about why
  Kotlin is a strong successor to Java
  `here <https://medium.com/@octskyward/why-kotlin-is-my-next-programming-language-c25c001e26e3>`_. If you're
  unfamiliar with Kotlin, there is an official
  `getting started guide <https://kotlinlang.org/docs/tutorials/>`_, and a series of
  `Kotlin Koans <https://kotlinlang.org/docs/tutorials/koans.html>`_

* IntelliJ IDEA is recommended due to the strength of its Kotlin integration.

* If an HA Bridge/Float deployment is required then a ``Zookeeper 3.5.4-Beta`` cluster will be required.
  Refer to :doc:`Hot-cold deployment <hot-cold-deployment>` and :doc:`Bridge configuration <bridge-configuration-file>`
  for more deployment information.

Following these software recommendations will minimize the number of errors you encounter, and make it easier for
others to provide support. However, if you do use other tools, we'd be interested to hear about any issues that arise.

Set-up instructions
-------------------
The instructions below will allow you to set up your development environment for running Corda and writing CorDapps. If
you have any issues, please reach out on `Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ or via
`our Slack channels <http://slack.corda.net/>`_.

The set-up instructions are available for the following platforms:

* :ref:`windows-label` (or `in video form <https://vimeo.com/217462250>`__)

* :ref:`mac-label` (or `in video form <https://vimeo.com/217462230>`__)

* :ref:`deb-ubuntu-label`

* :ref:`fedora-label`

.. note:: These setup instructions will guide you on how to install the Oracle JDK.

.. _windows-label:

Windows
-------

.. warning:: If you are using a Mac, Debian/Ubuntu or Fedora machine, please follow the :ref:`mac-label`, :ref:`deb-ubuntu-label` or :ref:`fedora-label` instructions instead.

Java
^^^^
1. Visit http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
2. Click the download link for jdk-8uXXX-windows-x64.exe (where "XXX" is the latest minor version number)
3. Download and run the executable to install Java (use the default settings)
4. Add Java to the PATH environment variable by following the instructions in the `Oracle documentation <https://docs.oracle.com/javase/7/docs/webnotes/install/windows/jdk-installation-windows.html#path>`_
5. Open a new command prompt and run ``java -version`` to test that Java is installed correctly

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
3. Ensure the Kotlin plugin in Intellij is updated to version |kotlin_version| (new installs will contains this version)

.. _mac-label:

Mac
---

.. warning:: If you are using a Windows, Debian/Ubuntu or Fedora machine, please follow the :ref:`windows-label`, :ref:`deb-ubuntu-label` or :ref:`fedora-label` instructions instead.

Java
^^^^
1. Visit http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
2. Click the download link for jdk-8uXXX-macosx-x64.dmg (where "XXX" is the latest minor version number)
3. Download and run the executable to install Java (use the default settings)
4. Open a new terminal window and run ``java -version`` to test that Java is installed correctly

IntelliJ
^^^^^^^^
1. Visit https://www.jetbrains.com/idea/download/download-thanks.html?platform=mac&code=IIC
2. Download and run the executable to install IntelliJ Community Edition (use the default settings)
3. Ensure the Kotlin plugin in IntelliJ is updated to version |kotlin_version| (new installs will contains this version)

Debian/Ubuntu
-------------

.. warning:: If you are using a Mac, Windows or Fedora machine, please follow the :ref:`mac-label`, :ref:`windows-label` or :ref:`fedora-label` instructions instead.

These instructions were tested on Ubuntu Desktop 18.04 LTS.

Java
^^^^
1. Open a new terminal and add the Oracle PPA to your repositories by typing ``sudo add-apt-repository ppa:webupd8team/java``. Press ENTER when prompted.
2. Update your packages list with the command ``sudo apt update``
3. Install the Oracle JDK 8 by typing ``sudo apt install oracle-java8-installer``. Press Y when prompted and agree to the licence terms.
4. Verify that the JDK was installed correctly by running ``java -version``

Git
^^^^
1. From the terminal, Git can be installed using apt with the command ``sudo apt install git``
2. Verify that git was installed correctly by typing ``git --version``

IntelliJ
^^^^^^^^
Jetbrains offers a pre-built snap package that allows for easy, one-step installation of IntelliJ onto Ubuntu.

1. To download the snap, navigate to https://snapcraft.io/intellij-idea-community
2. Click ``Install``, then ``View in Desktop Store``. Choose ``Ubuntu Software`` in the Launch Application window.
3. Ensure the Kotlin plugin in Intellij is updated to version |kotlin_version| (new installs will contains this version)

.. _fedora-label:

Fedora
------

.. warning:: If you are using a Mac, Windows or Debian/Ubuntu machine, please follow the :ref:`mac-label`, :ref:`windows-label` or :ref:`deb-ubuntu-label` instructions instead.

These instructions were tested on Fedora 28.

Java
^^^^
1. Download the RPM installation file of Oracle JDK from https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html.
2. Install the package with ``rpm -ivh jdk-<version>-linux-<architecture>.rpm`` or use the default software manager.
3. Choose java version by using the following command ``alternatives --config java``
4. Verify that the JDK was installed correctly by running ``java -version``

Git
^^^^
1. From the terminal, Git can be installed using dnf with the command ``sudo dnf install git``
2. Verify that git was installed correctly by typing ``git --version``

IntelliJ
^^^^^^^^
1. Visit https://www.jetbrains.com/idea/download/download-thanks.html?platform=linux&code=IIC
2. Unpack the ``tar.gz`` file using the following command ``tar xfz ideaIC-<version>.tar.gz -C /opt``
3. Run IntelliJ with ``/opt/ideaIC-<version>/bin/idea.sh``
4. Ensure the Kotlin plugin in IntelliJ is updated to version |kotlin_version| (new installs will contains this version)

Resolve Corda Enterprise binaries
---------------------------------
.. |developer_pack_name| replace:: corda-|version|-developer-pack.tar.gz

The Corda Enterprise binaries are not available in a publicly accessible Maven repository. Instead, the Corda Enterprise
binaries will be made available to your organisation as a compressed tarball (|developer_pack_name|).
This tarball contains all of the Corda dependencies as they would appear in your local Maven repository located at
``C:\Documents and Settings\{your-username}\.m2``.

To build CorDapps on development machines the Corda Enterprise binaries will need to be discoverable by Gradle. The
`build.gradle <https://github.com/corda/samples/tree/release-V4-enterprise/cordapp-example/build.gradle>`_ file in the Corda
samples repository (``release-V4-enterprise`` branch) includes instructions on how to allow Gradle to discover dependencies.

1. Open ``samples\cordapp-example\build.gradle``
2. Do any of the following to allow Gradle to resolve Corda Enterprise binaries, for more information read the commented code in ``build.gradle``:

   a. Add Corda Enterprise binaries and dependencies to your local maven repository path (e.g., ``C:\Documents and Settings\{your-username}\.m2``).
   b. Upload Corda Enterprise binaries and dependencies to your company's private Maven repository and register the repository with Gradle.
   c. Add Corda Enterprise binaries to a local directory and register a local Maven repository pointing to this directory with Gradle.

.. note:: Upon receiving the binaries, the quickest way to get started developing your CorDapps is **option a**. This can
be done by firstly unpacking the |developer_pack_name| compressed tarball. Then, copy the unpacked
          ``respository`` folder to your local Maven repository located at ``C:\Documents and Settings\{your-username}\.m2``.

Download and run a sample project
---------------------------------

Follow the instructions in https://docs.corda.net/tutorial-cordapp.html.

.. warning:: Ensure you checkout the corresponding branch for for Corda Enterprise |corda_version| by running ``git checkout release-V4-enterprise`` in the samples directory

CorDapp Templates and samples
-----------------------------

A CorDapp template that you can use as the basis for your own CorDapps is available in both Java and Kotlin versions:

    https://github.com/corda/cordapp-template-java.git

    https://github.com/corda/cordapp-template-kotlin.git

A comprehensive list of samples, including CorDapps written by R3 and community CorDapps and projects, are available here:

	https://www.corda.net/samples/

You can clone these repos to your local machine by running the command ``git clone [repo URL]``.

.. _deb-ubuntu-label:

Next steps
----------
The best way to check that everything is working fine is by taking a deeper look at the
:doc:`example CorDapp <tutorial-cordapp>`.

Next, you should read through :doc:`Corda Key Concepts <key-concepts>` to understand how Corda works.

By then, you'll be ready to start writing your own CorDapps. Learn how to do this in the
:doc:`Hello, World tutorial <hello-world-introduction>`. You may want to refer to the
:doc:`API documentation <corda-api>`, the :doc:`flow cookbook <flow-cookbook>` and the
`samples <https://www.corda.net/samples/>`_ along the way.

If you encounter any issues, please ask on `Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_ or via
`our Slack channels <http://slack.corda.net/>`_.
