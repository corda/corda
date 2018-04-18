# Cordapp Configuration Sample

This sample shows a simple example of how to use per-cordapp configuration. It includes;

* A configuration file
* Gradle build file to show how to install your Cordapp configuration
* A flow that consumes the Cordapp configuration

To run from the command line in Unix:

1. Run ``./gradlew samples:cordapp-configuration:deployNodes`` to create a set of configs and installs under 
   ``samples/cordapp-configuration/build/nodes``
2. Run ``./samples/cordapp-configuration/build/nodes/runnodes`` to open up three new terminals with the three nodes
3. At the shell prompt for Bank A or Bank B run ``start net.corda.configsample.GetStringConfigFlow configKey: someStringValue``.
   This will start the flow and read the `someStringValue` CorDapp config.

To run from the command line in Windows:

1. Run ``gradlew samples:cordapp-configuration:deployNodes`` to create a set of configs and installs under 
   ``samples\cordapp-configuration\build\nodes``
2. Run ``samples\cordapp-configuration\build\nodes\runnodes`` to open up three new terminals with the three nodes
3. At the shell prompt for Bank A or Bank B run ``start net.corda.configsample.GetStringConfigFlow configKey: someStringValue``.
   This will start the flow and read the `someStringValue` CorDapp config.
