Getting set up
==============

Software requirements
---------------------

Corda uses industry-standard tools to make set-up as simple as possible. Following the software recommendations below will 
minimize the number of errors you encounter, and make it easier for others to provide support. However, if you do use other tools, 
we're interested to hear about any issues that arise.

JVM
~~~

Corda is written in Kotlin and runs in a JVM. We develop against Oracle JDK 8, and other JVM implementations are not actively 
supported. Oracle JDK 8 can be obtained directly from 
`Oracle <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`_. Installation instructions are 
available for `Windows <http://docs.oracle.com/javase/8/docs/technotes/guides/install/windows_jdk_install.html#CHDEBCCJ>`_, 
`Linux <http://docs.oracle.com/javase/8/docs/technotes/guides/install/linux_jdk.html#BJFGGEFG>`_ and 
`OS X <http://docs.oracle.com/javase/8/docs/technotes/guides/install/mac_jdk.html#CHDBADCG>`_.

Please ensure that you keep your Oracle JDK installation updated to the latest version while working with Corda. 
Even earlier versions of JDK 8 versions can cause cryptic errors.

If you do choose to use OpenJDK instead of Oracle's JDK, you will also need to install OpenJFX.

Additional troubleshooting information can be found `here <https://docs.corda.net/getting-set-up-fault-finding.html#java-issues>`_.

Kotlin
~~~~~~

Applications on Corda (CorDapps) can be written in any JVM-targeting language. However, Corda itself and most of the samples 
are written in Kotlin. If you're unfamiliar with Kotlin, there is an official `getting started guide <https://kotlinlang.org/docs/tutorials/>`_. 
See also our :doc:`further-notes-on-kotlin`.

IDE
~~~

We strongly recommend the use of IntelliJ IDEA as an IDE, primarily due to the strength of its Kotlin integration. The free Community 
Edition can be downloaded from `JetBrains <https://www.jetbrains.com/idea/download/>`_.

Please make sure that you're running the latest version of IDEA, as older versions have been known to have problems integrating with Gradle, 
the build tool used by Corda.

You'll also want to install the Kotlin IDEA plugin by following the instructions 
`here <https://kotlinlang.org/docs/tutorials/getting-started.html>`_.

Additional troubleshooting information can be found `here <https://docs.corda.net/getting-set-up-fault-finding.html#idea-issues>`_.

Git
~~~

We use git to version-control Corda. Instructions on installing git can be found 
`here <https://git-scm.com/book/en/v2/Getting-Started-Installing-Git>`_.

Following these instructions will give you access to git via the command line. It can also be useful to control git via IDEA. Instructions 
for doing so can be found on the `JetBrains website <https://www.jetbrains.com/help/idea/2016.2/using-git-integration.html>`_.

Gradle
~~~~~~

We use Gradle as the build tool for Corda. However, you do not need to install Gradle itself, as a wrapper is provided.

The wrapper can be run from the command line by using ``./gradlew [taskName]`` on OS X/Linux, or ``gradlew.bat [taskName]`` on Windows.

Corda source code
-----------------

The Corda platform source code is available here:

    https://github.com/corda/corda.git

A CorDapp template that you can use as the basis for your own CorDapps is available here:

    https://github.com/corda/cordapp-template.git

And a simple example CorDapp for you to explore basic concepts is available here:

	https://github.com/corda/cordapp-tutorial.git

You can clone these repos to your local machine by running the command ``git clone [repo URL]``.

By default, these repos will be on the ``master`` branch. However, this is an unstable development branch. You should check 
out the latest release tag instead by running ``git checkout release-M9.0``.

Opening Corda/CorDapps in IDEA
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

When opening a Corda project for the first time from the IDEA splash screen, please click "Open" rather than "Import Project", 
and then import the Gradle project by clicking "Import Gradle project" in the popup bubble on the lower right-hand side of the screen. 
If you instead pick "Import Project" on the splash screen, a bug in IDEA will cause Corda's pre-packaged run configurations to be erased. 

If you see this warning too late, that's not a problem - just use ``git checkout .idea/runConfiguration`` or the version control tab in 
IDEA to undelete the files.

IDEA's build of the project may need to be resynced from time to time. This can be done from within IDEA by going to "View" -> "Tool Windows" -> "Gradle" 
and clicking "Refresh all Gradle projects". Whenever prompted about Gradle, accept the defaults suggested by IDEA.

Next steps
----------

The best way to check that everything is working fine is by :doc:`running-the-demos`.

Once you have these demos running, you may be interested in writing your own CorDapps, in which case you should refer to 
:doc:`tutorial-cordapp`.

If you encounter any issues, please see the :doc:`getting-set-up-fault-finding` page, or get in touch with us on the 
`forums <https://discourse.corda.net/>`_ or via `slack <http://slack.corda.net/>`_.