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

.. _windows-label:

.. note:: These setup instructions will guide you on how to install the Oracle JDK. Each JDK can be found on their respective sites:

          * `Oracle <http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html>`_

          * `Amazon Corretto <https://docs.aws.amazon.com/corretto/latest/corretto-8-ug/downloads-list.html>`_

          * `Red Hat OpenJDK <https://developers.redhat.com/products/openjdk/download/>`_

          * `Zulu OpenJDK <https://www.azul.com/downloads/zulu/>`_

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

.. _deb-ubuntu-label:

Debian/Ubuntu
-------------

.. warning:: If you are using a Mac, Windows or Fedora machine, please follow the :ref:`mac-label`, :ref:`windows-label` or :ref:`fedora-label` instructions instead.

These instructions were tested on Ubuntu Desktop 18.04 LTS.

Java
^^^^
1. Go to https://www.oracle.com/technetwork/java/javase/downloads/java-archive-javase8-2177648.html and download ``jdk-8u202-linux-x64.tar.gz``.
   You will be redirected to an Oracle login page. Login or create an account to download the JDK.
2. Extract the archive with ``tar -xvf jdk-8u202-linux-x64.tar.gz jdk1.8.0_202/``
3. Create a directory to place the JDK e.g. ``sudo mkdir -p /usr/lib/jvm``
4. Move the extracted archive in that folder with ``sudo mv ./jdk1.8.0_202 /usr/lib/jvm/``
5. Add the most common java binaries to the list of alternatives to allow easy swap between Java versions in the future

       .. code-block:: shell

         sudo update-alternatives --install "/usr/bin/java" "java" "/usr/lib/jvm/jdk1.8.0_202/bin/java" 1
         sudo update-alternatives --install "/usr/bin/javac" "javac" "/usr/lib/jvm/jdk1.8.0_202/bin/javac" 1
         sudo update-alternatives --install "/usr/bin/javaws" "javaws" "/usr/lib/jvm/jdk1.8.0_202/bin/javaws" 1

6. Update permissions

       .. code-block:: shell

         sudo chmod a+x /usr/bin/java
         sudo chmod a+x /usr/bin/javac
         sudo chmod a+x /usr/bin/javaws
         sudo chown -R root:root /usr/lib/jvm/jdk1.8.0_202

7. Set JAVA_HOME and JRE_HOME to your ``~/.bash_profile`` and/or ``~/.bashrc``

       .. code-block:: shell

         export JAVA_HOME=/usr/lib/jvm/jdk1.8.0_202
         export JRE_HOME=/usr/lib/jvm/jdk1.8.0_202/jre

8. Verify that the JDK was installed correctly by running ``java -version``

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
