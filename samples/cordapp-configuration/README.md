# Cordapp Configuration Sample

This sample shows a simple example of how to use per-cordapp configuration. It includes;

* A configuration file
* Gradle build file to show how to install your Cordapp configuration
* A flow that consumes the Cordapp configuration

## Usage

To run the sample you must first build it from the project root with;

    ./gradlew deployNodes
    
This will deploy the node with the configuration installed. 
The relevant section is the ``deployNodes`` task.

## Running

* Windows: `build\nodes\runnodes`
* Mac/Linux: `./build/nodes/runnodes`

Once the nodes have started up and show a prompt you can now run your flow. 