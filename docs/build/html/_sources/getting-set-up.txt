Getting set up
==============

Install the Oracle JDK 8u45 or higher. OpenJDK will probably also work but I'm not testing with that.

Then install IntelliJ version 15 community edition:

   https://www.jetbrains.com/idea/download/

Upgrade the Kotlin plugin to the latest version (1.0-beta-4584) by clicking "Configure > Plugins" in the opening screen,
then clicking "Install JetBrains plugin", then searching for Kotlin, then hitting "Upgrade" and then "Restart".

Choose "Check out from version control" and use this git URL

     https://your_username@bitbucket.org/R3-CEV/r3repository.git

Agree to the defaults for importing a Gradle project. Wait for it to think and download the dependencies.

Right click on the tests directory, click "Run -> All Tests" (note: NOT the first item in the submenu that has the
gradle logo next to it).

The code should build, the unit tests should show as all green.

You can catch up with the latest code by selecting "VCS -> Update Project" in the menu.

If IntelliJ complains about lack of an SDK
------------------------------------------

If on attempting to open the project, IntelliJ refuses because SDK was not selected, do the following:

   Configure -> Project Defaults -> Project Structure

on that tab:

   Project Settings / Project

click on New… next to the red <No SDK> symbol, and select JDK.  It should then pop up and show the latest JDK it has
found at something like

    jdk1.8.0_xx…/Contents/Home

Also select Project language level: as 8.  Click OK.  Open should now work.

Doing it without IntelliJ
-------------------------

If you don't want to explore or modify the code in a local IDE, you can also just use the command line and a text editor:

* Run ``./gradlew test`` to run the unit tests.
* Run ``git pull`` to upgrade