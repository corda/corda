# CorDapp Minimum and Target Platform Version

## Overview

We want to give CorDapps the ability to specify which versions of the platform they support.

This will make it easier for CorDapp developers to support multiple platform versions, and enable CorDapp developers to ["tweak behaviour and [...] opt in to changes that might be breaking (e.g. sandboxing)"](https://cordaledger.slack.com/archives/C3J04VC3V/p1534170356000500).

This document proposes that CorDapps will have metadata associated with them specifying a minimum mlatform version and a target platform Version. The minimum platform version of a CorDapp would indicate that a Corda Node would have to be running at least this version of the Corda platform in order to be able to run this CorDapp. The target platform version of a CorDapp would indicate that it was tested for this version of the Corda Platform.

For reference, see [this ticket in Jira](https://r3-cev.atlassian.net/browse/CORDA-470).

## Background

* *Platform version (Corda)* An integer representing the API version of the Corda platform ([see docs](https://docs.corda.net/head/versioning.html#versioning)). 

* *Platform version (Node)* The value of the Corda platform version that a node is running and advertising to the network.

* *Minimum platform version (Network)* Set by the network zone operator. The minimum platform version is distributed with the network parameters as `minimumPlatformVersion`.
 [From the docs:](https://docs.corda.net/network-map.html#network-parameters)
> 	The minimum platform version that the nodes must
 
* *Minimum target version (Network)* Does not exist yet. We are planning to introduce the minimum target Version as part of the network parameters. This document assumes that it indicates to nodes in the network that they should not run CorDapps with a target version lower than this.

* *Target platform version (CorDapp)* Introduced in this document. Indicates that a CorDapp was tested with this version of the Corda Platform and should be run at this API level if possible.

* *Minimum platform version (CorDapp)* Introduced in this document. Indicates the minimum version of the Corda platform that a Corda Node has to run in order to be able to run a CorDapp.

## Goals

Define the semantics of target platform version and minimum platform version attributes for CorDapps. Describe how target and platform versions would be specified by CorDapp developers. Define how these values can be accessed by the node and the CorDapp itself.

## Non-goals

In the future it might make sense to integrate the minimum and target versions into a Corda gradle plugin. Such a plugin is out of scope of this document.

This document does not concern itself with how the _minimum target version_ should work.

## Timeline

This is intended as a long-term solution. 

## Requirements
  
* The CorDapp's minimum and target platform version must be accessible to nodes at CorDapp load time, so they decide whether to load this CorDapp or not and to make the minimum and target version information available ([see Jira](https://r3-cev.atlassian.net/browse/CORDA-470))  .

* The node's platform version must be accessible to CorDapps

* The CorDapp's target platform version must be accessible to the node when running CorDapps.

## Design Decisions

 Corda nodes running a platform version that is lower than the version which will introduce the changes described in this document will not check the minimum version of CorDapps. Thus, they will attempt to run CorDapps with a `minPlatformVersion` higher than their platform version. 
To prevent this, the check for `minPlatformVersion` could be performed in the CorDapp as well as in the node. When a CorDapp detects that a node which is not fulfilling its `minPlatformVersion` requirement is attempting to run it, it could stop itself from being loaded (or react in a less drastic fashion). However, it may be desirable that the same CorDapp will keep working on some other, older nodes that are located in a different, private, network with fewer restrictions in place. 
To allow for flexibility it seems reasonable to let CorDapp developers decide how to handle this situation.


## Design

### Testing 

Do CorDapp developers have to test their CorDapps on all platform versions v, `minPlatformVersion` <= v <= `targetPlatformVersion`?

Developers will need to make sure that their CorDapp runs on a newer platform version. A procedure that can be followed when a new platform version is released should be included in the documentation as a best practise.

* Suppose our target platform version is 5, and a newer platform version 6 is released. We want to find out if our CorDapp works as expected (without raising the target version)
* A procedure could look like this:
  - A new platform version is released/ announced (network/ nodes not upgraded yet)
  - CorDapp developers build their CorDapp against the new version, run/ test against nodes running the old/current version and nodes running the new versions. If this works, they can raise the target version of their CorDapp and test again.
  - CorDapp developers test their old build against a node running the new versions
    
### Implications for platform developers

* Should we/ can we make sure CorDapps do not call APIs newer than their target Version
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

* Will all changes to existing APIs neet to receive version guards, or only some (breaking) changes?

* Changes to the Corda Platform that are not directly tied to the node calling the API may also need version guards.

## Technical Design

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

## Observations/ Open Questions

Changing/ Upgrading minimum and target version of a CorDapp: Should there be any constraints? (e.g. the target version of version 11 of a CorDapp must be greater than the target version of version 10)

Interactions between minimum and target version and CorDapp/ Flow versioning? Anything to consider/ be aware of? https://docs.corda.net/head/upgrading-cordapps.html# 

https://docs.corda.net/head/versioning.html#versioning

The version information advertised by nodes and CorDapps is not guaranteed to be correct. This is merely a convention and it is strongly advised that this convention be followed. This must be considered especially when thinking about security. It is not possible to enforce that a node advertising it's running a certain platform version is _actually_ running it. Same for CorDapp version(s). 

The minPlatformVersion constrains the usage of the API to that API level:

```
// in CorDapp
if(NODE_PLATFORM_VERSION < CORDAPP_TARGET_VERSION)
    oldApi()
else
    newApi() // Problem: `newApi()` does not exist on the node, can't load CorDapp   
    
```

There is no guarantee with which API version a node is going to run a CorDapp, the Target Version merely indicates a preference (?). 

Example: Let's assume that we have a CorDapp with minimum platform version 5 and target platform version 7
  * A node running platform version 4 will not load this CorDapp

  * A node running platform version 5 will load and run this CorDapp (at API level 5)

  * A node running platform version 6 Will load and run this CorDapp (at API level 6)

  * A node running platform version 7 Will load and run this CorDapp (at API level 7)

  * A node running platform version 8 Will load and run this CorDapp (at API level 7)

Example: Let's assume that we have a CorDapp with minimum platform version 5 and target platform version 6. A node advertising that it is running platform version 6 on a network with minimum platform version 4 would be free to choose between running our CorDapp on platform version 5 or platform version 6 (TODO should we define a convention for this?).


Additional benefit: Third-party libraries can be made backwards-compatible. 

It will be possible to do linting to detect when trying to use APIs from a newer version
