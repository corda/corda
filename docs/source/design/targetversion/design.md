# CorDapp Minimum and Target Platform Version

## Overview

We want to give CorDapps the ability to specify which versions of the platform they support.

This will make it easier for CorDapp developers to support multiple platform versions, and enable CorDapp developers to ["tweak behaviour and [...] opt in to changes that might be breaking (e.g. sandboxing)"](https://cordaledger.slack.com/archives/C3J04VC3V/p1534170356000500).

## Background

CorDapps should have metadata specifying the minimum platform version required to run them, as well as a target version indicating the platform version for which they were tested.

For reference, see the corresponding [discussion on the #design channel](https://cordaledger.slack.com/archives/C3J04VC3V/p1534169936000321) and [this ticket in Jira](https://r3-cev.atlassian.net/browse/CORDA-470).

Both values need to be accesible from within the CorDapp and from the node's CorDapp loader.

## Goals

Describe how target and platform versions would be specified by CorDapp developers. Define how these values can be accessed by the node and the CorDapp itself.  

## Non-goals

In the future it might make sense to integrate the minimum and target versions into a Corda gradle plugin. Such a plugin is out of scope of this document.

## Timeline

This is intended as a long-term solution. Where and how the version information is stored could be changed once we migrate to Java 9

## Requirements
  
* The node should be able to check the minimum and platform version when loading the Cordapp ([see Jira](https://r3-cev.atlassian.net/browse/CORDA-470))  

## Design Decisions




* How are the minimum- and target version stored and encoded?
    * The minimum- and target platform version are written to the Manifest of the CorDapps JAR.
    * For now it would be enough to specify them as properties and add them to the JAR's manifest in gradle:
    ```
    ext {
     targetPlatformVersion=4
     minPlatformVersion=2
    }
    ```
    
    ```
    jar {
        manifest {
            attributes(
                    'Min-Platform-Version': minPlatformVersion,
                    'Target-Platform-Version': targetPlatformVersion
            )
        }
    }
    ```
* How can code inside the node find out the minimum and target version of the app that is calling it?
    * The node's CorDapp loader reads these values from the Manifest when loading the CorDapp
    * It should be up to the node operator how this error is handled (what should be the default?)
        * Log an error and shut down the node
        * Log a warning and continue without loading the CorDapp
    * Would it be enough to add them to `net.corda.core.cordapp.Cordapp`?
* How can code inside the CorDapp find out its target version?
    * If this information is in `Cordapp`, it can be obtained via the `CorDappContext` from the serviceHub. (Is this enough, or should it be a globally accesible constant?)
* The node's platform version is already accessible to CorDapps `serviceHub.nodeInfo`

* The check for minPlatformVersion should be performed in the CorDapp as well as in the Node.

## Design

Testing: How can developers make sure that their CorDapp runs on a newer platform version?

Suppose our target platform version is 5, and a newer platform version 6 is released. We want to find out if our CorDapp works as expected (without raising the target version)

Implications for platform developers:

* Make sure CorDapps do not call APIs newer than their target Version
    Check at CorDapp load time?
    
* When modifying existing APIs, do target version checks
    ```
    // in node
    if( targetVersion < SOME_SPECIFIC_VERSION ) {
        oldBehaviour()
    } else {
        newBehaviour()
    }
    ```

Further considerations:

* Additional benefit: Third-party libraries can be made backwards-compatible. 
* Linting: detect when trying to use APIs from a newer version
