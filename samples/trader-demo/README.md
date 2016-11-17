# Trader Demo 

Please see docs/build/html/running-the-demos.html

This program is a simple driver for exercising the two party trading protocol. Until Corda has a unified node server
programs like this are required to wire up the pieces and run a demo scenario end to end.

If you are creating a new scenario, you can use this program as a template for creating your own driver. Make sure to
copy/paste the right parts of the build.gradle file to make sure it gets a script to run it deposited in
build/install/r3prototyping/bin

In this scenario, a buyer wants to purchase some commercial paper by swapping his cash for the CP. The seller learns
that the buyer exists, and sends them a message to kick off the trade. The seller, having obtained his CP, then quits
and the buyer goes back to waiting. The buyer will sell as much CP as he can!

The different roles in the scenario this program can adopt are:

This template contains the build system and an example application required to get started with [Corda](http://todo.todo).

## Prerequisites

You will need to have [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) 
installed and available on your path.

## Getting Started

First clone this repository and the Corda repository locally. Then open a terminal window in the Corda directory and run:
 
Unix: 

     ./gradlew publishToMavenLocal
     
Windows:

     gradle.bat publishToMavenLocal
     
This will publish a copy of Corda to your local Maven repository for your Cordapp to use. Next open a terminal window
in your Cordapp directory (this one) and run:

Unix:

     ./gradlew deployNodes
     
Windows:

     gradlew.bat deployNodes
     
This command will create several nodes in `build/nodes` that you can now run with:

Unix:

     cd build/nodes
     ./runnodes

Windows:

Windows users currently have to manually enter each directory in `build/nodes` and run `java -jar corda.jar` in each.
This will be updated soon.

This will now have nodes running on your machine running this Cordapp. You can now begin developing your Cordapp. 

## Further Reading

Tutorials and developer docs for Cordapps and Corda are [here](https://docs.corda.r3cev.com).