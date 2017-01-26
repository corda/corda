Getting set up
==============

We have tried to make access to Corda as relatively simple as possible, using industry standard established tools.
Although it is possible to replace any of the recommendations below, we will find it a lot easier to support your efforts
if you follow our guidelines. Saying that, we are also interested in problems that arise due to different configurations.

A JVM
-----

Corda runs in a JVM and is written predominantly in Kotlin with some example use cases demonstrated in Java that we have
incorporated to demonstrate that Kotlin and Java can work seamlessly together. We recommend the most recent production
version of Java 8. The JDK can be obtained `from Oracle <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`_.
Other implementations of the JVM are not actively supported, but as mentioned, we are interested in finding out any issues you
do have with them.

.. note:: If you are using a JVM implementation other than Oracle's you may get errors similar to ``Unresolved reference: javafx``.
   This means JavaFX is not bundled with the JVM and you will need to install it separately (e.g. OpenJFX is needed
   with OpenJDK).

IntelliJ
--------
We strongly recommend the use of IntelliJ's Development Environment known as IDEA. Download it for free from
`JetBrains <https://www.jetbrains.com/idea/download/>`_. The primary reason we recommend this particular IDE is that it integrates
very well with our choice of language for Corda, "Kotlin", as JetBrains also support the development of Kotlin.

.. warning:: When opening the Corda project for the first time from the IntelliJ splash screen, please use "Open"
   and then agree to import the Gradle project from the popup bubble. Don't pick "Import" on the splash screen,
   because a bug in IntelliJ will cause the pre-packaged run configurations to be erased. If you see this warning
   too late, it's no problem, just use ``git checkout .idea/runConfiguration`` or the version control tab in IntelliJ
   to undelete the files.


Kotlin
------
Kotlin is available as a downloadable plugin to IntelliJ. Refer to IntelliJ's instructions on
`getting Started with Kotlin and IntelliJ <https://kotlinlang.org/docs/tutorials/getting-started.html>`_. Additionally,
if you would like to start getting to grips with the Kotlin language, then we strongly recommend you work through some
of the tutorials (known as "koans") as well. Also see our :doc:`further-notes-on-kotlin`.


Version control via Git
-----------------------

We use git to version control Corda. The authoritative place to obtain git is from the main `git website <https://git-scm.com/downloads>`_
but it may be the case that your operating system provides git with a supported utility (e.g. for Apple, git is provided along
with XCode - their free development environment). If this is the case, we would recommend you obtain git via that
supported route.

You will need the command line package installed which you can then use natively (via the command line) or via IntelliJ
(in which case you may need to configure IntelliJ to recognise where git has been installed on your system). IntelliJ and
git configuration are quite seamless although the first time you use it, you will have to configure IntelliJ the location
of your git command installation. More details regarding this can be found
on the `JetBrains website <https://www.jetbrains.com/help/idea/2016.2/using-git-integration.html>`_

Gradle
------

Gradle is our primary means of building Corda and managing dependencies. IntelliJ has its own view of this and occasionally
may need to be resynced from time to time. This can be done within IntelliJ by pressing the "gradle refresh" icon located
on the gradle tab (generally found on the right hand side), or by following the gradle commands specific for the task you
are performing (details expounded later). Whenever prompted about gradle, accept the defaults suggested by IntelliJ.


Corda source code
-----------------

You can check out the Corda platform source code from this repository:

    https://github.com/corda/corda.git

and a template app that you can use as a basis for experimenting with app development from:

    https://github.com/corda/cordapp-template.git

You can catch up with the latest code by selecting "VCS -> Update Project" in the IntelliJ menu.


Troubleshooting
---------------

See :doc:`getting-set-up-fault-finding`, or get in touch with us either on the `forums <https://discourse.corda.net/>`_ or via `slack <http://slack.corda.net/>`_.

