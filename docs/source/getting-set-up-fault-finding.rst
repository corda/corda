Getting Set Up : Faultfinding
=============================


If IntelliJ complains about lack of an SDK
------------------------------------------

If on attempting to open the project (including importing Gradle project), IntelliJ refuses because SDK was not selected, do the following:

   Configure -> Project Defaults -> Project Structure

on that tab:

   Project Settings / Project

click on New… next to the red <No SDK> symbol, and select JDK.  It should then pop up and show the latest JDK it has
found at something like

    jdk1.8.0_xx…/Contents/Home

Also select Project language level: as 8.  Click OK.  Open should now work.

Kotlin installation issues
--------------------------

If you are having trouble installing Kotlin, first try upgrading the Kotlin plugin to the latest version by clicking
"Configure > Plugins" in the opening screen, then clicking "Install JetBrains plugin", then searching for Kotlin,
then hitting "Upgrade" and then "Restart". You can confirm what is the latest version of Kotlin plugin on this page:

    https://plugins.jetbrains.com/plugin/6954


Gradle issues
-------------

After you have updated your code to the latest version from git, ensure that Gradle project is imported.

You should have the "Unliked Gradle project?" pop-up window in the IntelliJ top right corner. Please click on "Import Gradle Project". Wait for it to think and download the dependencies. After that you might have another popup titled "Unindexed remote maven repositories found." This is general IntelliJ question and doesn't affect Corda, therefore you can decided to index them or not.

Next click on "green arrow" next to "All tests" pop-up on the top toolbar.

The code should build, the unit tests should show as all green.



Doing it without IntelliJ
-------------------------

If you don't want to explore or modify the code in a local IDE, you can also just use the command line and a text editor:
* First run ``git clone https://your_username@bitbucket.org/R3-CEV/r3prototyping.git`` to download Corda source code. Please remember to replace your_username with your actual bitbucket user name.
* Next ensure that you are in r3repository ``cd r3repository``
* Then you can run ``./gradlew test`` to run the unit tests.
* Finally remeber to run ``git pull`` to upgrade the source code.
