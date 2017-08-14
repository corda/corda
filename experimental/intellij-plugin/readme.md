# Corda Intellij Plugin

Corda Intellij Plugin is a plugin for Intellij Idea IDE which aid Corda application development.

## Features
* Setup CorDapp project in a few clicks.
* Support Java and Kotlin project.
* User can choose to include different Corda module templates.

## Getting started
 This project uses gradle to manage dependencies, Intellij's Plugin runner DOES NOT work in this project.
 
 ###Running the project
 You can run or debug the project using provided Intellij Run Configuration `CordaPlugin` or by using the gradle command
 `./gradlew runIde` IDE's log file is located in `build/idea-sandbox/system/log/idea.log`

 ###Corda Flow Tool
 After the IntelliJ (with the Corda plugin) is started, go to View/Tool Windows in the menu bar and in the list of
 available tools you should see the 'Corda Flow Tool'. In the tool window, point to the flow snapshot location
 (or node's base directory) and you will be able to see and examine all available flow snapshots.
 
## TODOs
* Create a higher quality Corda icon. 
* Create a more compact kotlin CorDapp template. 
* Add Java Templates.
* Figure out how to import gradle setting in the plugin, to avoid having to manual import after project creation.
* Support other build tools? (Maven, SBT etc...)
* Custom run configuration for running and debugging CorDapp in Intellij.
* Add Python option?
* Flow visualiser?