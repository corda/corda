Troubleshooting
===============

IntelliJ issues
---------------

Run configurations are missing
******************************

If you opened the Corda project using "Import" from the IntelliJ splash screen rather than using "Open" and then
importing the Gradle build system from the popup bubble, then a bug in IntelliJ will cause it to wipe and recreate
the ``.idea`` directory where the run configurations are stored. The fix is simple and doesn't require you to
re-import the project: just undelete the files! You can do that by either:

1. Running ``git checkout .idea/runConfigurations`` to restore that part of the tree to its normal state.
2. Using the "Version Control" pane in IntelliJ to undelete the files via the GUI.

If IntelliJ complains about lack of an SDK
******************************************

If on attempting to open the project (including importing Gradle project), IntelliJ refuses because an SDK was not selected,
you may need to fix the project structure. Do this by following  `these instructions <https://www.jetbrains.com/help/idea/2016.2/configuring-global-project-and-module-sdks.html>`_. The correct JDK is often found at a path such as ``jdk1.8.0_xx…/Contents/Home``

Ensure that you have the Project language level set at as 8. If you are having trouble selecting the correct JDK, the
JetBrains website offers the `following guidelines <https://intellij-support.jetbrains.com/hc/en-us/articles/206544879-Selecting-the-JDK-version-the-IDE-will-run-under>`_.

Kotlin issues
-------------

Installation
************

There are two ways to configure Kotlin from IntelliJ. One way is via the initial project opening screen in which you will
need to use the ``Configure > Plugins`` tab. The other way is when you are in an open project, then you will need to
configure it via (on Mac) ``IntelliJ -> Preferences ...``, whereas on PC it is ``File -> Settings``. Select the plugins
bar, confirm that Kotlin is installed and up to date.

If you are having trouble installing Kotlin, first try upgrading the Kotlin plugin. At the time of writing, you can
confirm what is the latest version of the Kotlin plugin on `this page <https://plugins.jetbrains.com/plugin/6954>`_.


Gradle issues
-------------

Gradle within IntelliJ
**********************

After you have updated your code to the latest version from git, ensure that the gradle project is imported. Although
gradle is used via the command line, it is also integrated with IntelliJ in order for IntelliJ to determine dependencies
and index the project correctly.

When opening a project for the first time, you should see the "Unlinked Gradle project?" pop-up window in the IntelliJ top
right corner or in a popup alert window. If you miss this, it will also appear in the "Event Log" windows which can be
opened by clicking on "Event Log" at the bottom right of the IntelliJ window. Either way, click on "Import Gradle Project".

.. image:: resources/unlinked-gradle.png
    :height: 50 px
    :width: 410 px
    :alt: IntelliJ Gradle Prompt

Wait for it to think and download the dependencies. After that you might have another popup titled "Unindexed remote maven repositories found." This is a general IntelliJ question and doesn't affect Corda, therefore you can decided to index them or not. Next click on the "green arrow" next to "All tests" pop-up on the top toolbar.

The code should build, the unit tests should show as all green.

If still have problems, the JetBrains website has more information on `gradle here <https://www.jetbrains.com/help/idea/2016.2/working-with-gradle-projects.html>`_.

Gradle via the CLI
******************

Gradle commands can also be run from the command line - further details of command line gradle can be found `here <https://docs.gradle.org/current/userguide/gradle_command_line.html>`_.

Doing it without IntelliJ
-------------------------

If you don't want to explore or modify the code in a local IDE, you can also just use the command line and a text editor:

* First run ``git clone https://github.com/corda/corda`` to download Corda core source code

* Next ensure that you are in correct directory ``cd corda``

* Then you can run ``./gradlew test`` to run the unit tests.

* Finally remember to run ``git pull`` occasionally to upgrade the source code to the latest revision
