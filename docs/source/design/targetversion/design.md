# CorDapp Platform Version

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

This is intended as a long-term solution.

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
    * produces an error and refuses to load the CorDapp when values are invalid
    * Would it be enough to add them to `net.corda.core.cordapp.CorDapp`?
* How can code inside the CorDapp find out its target version?
    * If this information is in `CorDapp`, it can be obtained via the `CorDappContext` from the serviceHub.
* The node's platform version is already accessible to CorDapps `serviceHub.nodeInfo`

List of design decisions identified in defining the target solution.

For each item, please complete the attached [Design Decision template](decisions/decision.md)

## Design

Think about:

* backwards compatibility impact.

* Data model & serialization impact and changes required.

* Versioning, upgradability, migration=
* Management: audit, alerting, monitoring, backup/recovery, archiving

* Logging

* Testability

Introduce `targetPlatformversion` and `minPlatformVersion` 