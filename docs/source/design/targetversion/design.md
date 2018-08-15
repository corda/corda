# CorDapp Minimum and Target Platform Version

## Overview

We want to give CorDapps the ability to specify which versions of the platform they support.

This will make it easier for CorDapp developers to support multiple platform versions, and enable CorDapp developers to ["tweak behaviour and [...] opt in to changes that might be breaking (e.g. sandboxing)"](https://cordaledger.slack.com/archives/C3J04VC3V/p1534170356000500).

This document proposes that CorDapps will have metadata associated with them specifying a Minimum Platform Version and a Target Platform Version. The Minimum Platform Version of a CorDapp would indicate that a Corda Node would have to be running at least this version of the Corda platform in order to be able to run this CorDapp. The Target Platform Version of a CorDapp would indicate that it was tested for this Version of the Corda Platform.

For reference, see the corresponding [discussion on the #design channel](https://cordaledger.slack.com/archives/C3J04VC3V/p1534169936000321) and [this ticket in Jira](https://r3-cev.atlassian.net/browse/CORDA-470).

## Background

### Platform Version (Corda)
 The platform version is an integer representing the API version of the Corda platform ([see docs](https://docs.corda.net/head/versioning.html#versioning)). 

### Platform Version (Node)

The value of the Corda Platform Version that a node is running and advertising to the network.

### Minimum Platform Version (Network)

Set by the network zone operator. The minimum platform version is distributed with the network parameters as `minimumPlatformVersion`.
 [From the docs:](https://docs.corda.net/network-map.html#network-parameters)
> 	The minimum platform version that the nodes must be running. Any node which is below this will not start.
 
### Minimum Target Version (Network)

Does not exist yet. We are planning to introduce the Minimum Target Version as part of the Network Parameters. This document assumes that the semantics of `minimumTargetVersion` would be similar to `minimumPlatformVersion`, i.e. nodes with a target version lower than the network's minimum target version will refuse to start.

### CorDapp Versioning

(https://docs.corda.net/head/versioning.html#versioning)

### Target Platform Version (CorDapp)

Introduced in this document. Indicates that a CorDapp was tested with this version of the Corda Platform and should be run at this API level if possible.

### Minimum Platform Version (CorDapp)

Introduced in this document. Indicates the minimum version of the Corda platform that a Corda Node has to run in order to be able to run a CorDapp.


## Goals

Define the semantics of Target Platform Version and Minimum Platform Version attributes for CorDapps.
~Describe how target and platform versions would be specified by CorDapp developers. Define how these values can be accessed by the node and the CorDapp itself.~  

## Non-goals

In the future it might make sense to integrate the minimum and target versions into a Corda gradle plugin. Such a plugin is out of scope of this document.

This document does not concern itself with how the _Minimum Target Version_ should work.

## Timeline

This is intended as a long-term solution. Where and how the version information is stored could be changed once we migrate to Java 9

## Requirements
  
* The node should be able to check the minimum and platform version when loading the Cordapp ([see Jira](https://r3-cev.atlassian.net/browse/CORDA-470))  

* The node's Platform Version must be accessible to CorDapps

* The CorDapp's Minimum Platform Version must be accessible to nodes at CorDapp load time, so they decide whether to load this CorDapp or not.

* The CorDapp's Target Platform Version must be accessible to the node when running CorDapps.

## Design Decisions

Should the check for minPlatformVersion be performed in the CorDapp as well as in the Node?

## Design

Testing: How can developers make sure that their CorDapp runs on a newer platform version? / Procedure when new Platform Version is released

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

Open Questions

What about the release-version? 
>  The release version is still useful and every MQ message the node sends attaches it to the release-version header property for debugging purposes.

Changing/ Upgrading minimum and target version of a CorDapp: Should there be any constraints? (e.g. the target version of version 11 of a CorDapp must be greater than the target version of version 10)

Interactions between minimum and target version and CorDapp/ Flow versioning? Anything to consider/ be aware of? https://docs.corda.net/head/upgrading-cordapps.html# 

Implementation concerns

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
    * If this information is in `Cordapp`, it can be obtained via the `CorDappContext` from the serviceHub.
* The node's platform version is already accessible to CorDapps `serviceHub.nodeInfo`

Examples

Let's assume that we have a CorDapp with Minimum Platform Version 5 and Target Platform Version 7
A node running Platform Version 4 will not load this CorDapp
A node running Platform Version 5 will load and run this CorDapp
A node running Platform Version 6 Will load and run this CorDapp (at API level 6)
A node running Platform Version 7 Will load and run this CorDapp (at API level 7)
A node running Platform Version 8 Will load and run this CorDapp (at API level 7)




```
// in CorDapp
if(NODE_PLATFORM_VERSION < CORDAPP_TARGET_VERSION)
    oldApi()
else
    newApi() // Problem: `newApi()` does not exist on the node, can't load CorDapp   
    
```
Observations

There is no guarantee with which API version a node is going to run a CorDapp, the Target Version merely indicates a preference (?). 

Let's assume that we have a CorDapp with Minimum Platform Version 5 and Target Platform Version 6. A node advertising that it is running Platform Version 6 on a network with Minimum Platform Version 4 would be free to choose between running our CorDapp on Platform Version 5 or Platform Version 6.


CorDapp developers have to test their CorDapps on all Platform Versions v, `minPlatformVersion` <= v <= `targetPlatformVersion`

