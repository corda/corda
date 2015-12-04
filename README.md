# Introduction

This source repository contains explorations of various design concepts the R3 DLG is exploring.

Things you need to know:

* The main code documentation is in the form of a website in the git repository. Load [the website](docs/build/html/index.html)
  in the `docs/build/html` directory to start reading about what's included, how to get set up, and to read a tutorial
  on writing smart contracts in this framework.
  
* The architecture documentation is on the [Architecture Working Group Wiki](https://r3-cev.atlassian.net/wiki/display/AWG/Architecture+Working+Group) site - please
  refer to that for an explanation of some of the background concepts that the prototype is exploring.

* The code is a JVM project written mostly in [Kotlin](https://kotlinlang.org/), which you can think of as a simpler
  version of Scala (or alternatively, a much better syntax for Java). Kotlin can be learned quickly and is designed
  to be readable, so you won't need to know it very well to understand what the code is doing. If you'd like to
  add new features, please read its documentation on the website.

  There is also Java code included, to demonstrate how to use the framework from a more familiar language.

* For bug tracking we use [JIRA](https://r3-cev.atlassian.net/secure/RapidBoard.jspa?rapidView=9&projectKey=PD). For source control we use this BitBucket
  repository. You should have received credentials for these
  services as part of getting set up. If you don't have access, please contact Richard Brown or James Carlyle.

* There will be a mailing list for discussion, brainstorming etc called [r3dlg-awg](https://groups.google.com/forum/#!forum/r3dlg-awg). 

# Instructions for installing prerequisite software

## JDK  for Java 8

Install the Oracle JDK 8u45 or higher. It is possible that OpenJDK will also work but we have not tested with this.

## Using IntelliJ

It's a good idea to use a modern IDE.  We use IntelliJ.  Install IntelliJ version 15 community edition (which is free):

    https://www.jetbrains.com/idea/download/
    
Upgrade the Kotlin plugin to the latest version (1.0-beta-2423) by clicking "Configure > Plugins" in the opening screen, 
then clicking "Install JetBrains plugin", then searching for Kotlin, then hitting "Upgrade" and then "Restart".

Choose "Check out from version control" and use this git URL

    https://your_username@bitbucket.org/R3-CEV/r3prototyping.git

Agree to the defaults for importing a Gradle project. Wait for it to download the dependencies.

Right click on the tests directory, click "Run -> All Tests" (note: NOT the first item in the submenu that has the gradle logo next to it).

The code should build, the unit tests should show as all green.

You can catch up with the latest code by selecting "VCS -> Update Project" in the menu.

## IntelliJ Troubleshooting
If on attempting to open the project, IntelliJ refuses because SDK was not selected, do the following:

    Configure -> Project Defaults -> Project Structure

on that tab:

    Project Settings / Project

click on New… next to the red <No SDK> symbol, and select JDK.  It should then pop up and show the latest JDK it has found at something like

    jdk1.8.0_xx…/Contents/Home

Also select Project language level: as 8.  Click OK.  Open should now work.

## Accessing Source Without an IDE

If you don't want to explore or modify the code in a local IDE, you can also just use the command line and a text editor:

    git clone https://your_username@bitbucket.org/R3-CEV/r3prototyping.git

You will need to have your Bitbucket account set up already from R3. Then:

    cd r3prototyping

Run the following to run the unit tests:

    ./gradlew test

For the first time only, this will download and configure Gradle.
Run "git pull" to upgrade

## Starting point - the Tutorial

We have prepared a comprehensive tutorial.
One you have access to the source, open the following in a browser:

    r3prototyping/docs/build/html/index.html
 
![Screenshot](https://r3-cev.atlassian.net/wiki/download/attachments/3441064/Screen%20Shot%202015-12-10%20at%2010.43.06.png)