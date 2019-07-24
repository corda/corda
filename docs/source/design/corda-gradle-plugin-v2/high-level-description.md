# Existing solution and new requirements 
 
This document describes what is currently available, problems with this approach and new requirements.
The proposed solutions are described in separate documents. 
  
 
## Current CorDapp plugin 

The original CorDapp plugin was created before sophisticated library dependency and packaging requirements became apparent.
The main requirement was to package all dependencies that are not "corda" into a "semi-fat" jar.

To keep it simple under those requirements, its approach is to extend the functionality of the standard java plugin by giving extra packaging responsibilities to the basic dependency
configurations. 
See: https://docs.gradle.org/current/userguide/java_plugin.html and the newer https://docs.gradle.org/current/userguide/java_library_plugin.html. (These are the plugins that define the standard functionality one expects for a java project.)

In order to differentiate between dependencies that are "corda" and ones that are not, a ``corda`` prefix is added to the ``compile`` and ``runtime`` default dependencies.  

These are the current configurations and what they do:

- ``compile``       : both compile against *and fat-jar* this library.
- ``runtime``       : only fat-jar it - does not compile against it.
- ``cordaCompile``  : compile against this, but don't fat-jar.
- ``cordaRuntime``  : the `corda` prefix is preventing the library to be fat-jarred, and `runtime` to compile against. These libraries are used during tests and by the ``cordFormation`` plugin.
- ``cordapp``       : compile against it, but don't fat-jar and also deploy it to ``cordformation`` nodes.
 
As can be seen from the descriptions, the configuration names don't help too much in explaining their behaviour, because the original 
requirement of "corda" and "non-corda" dependency is not something that is a general concern of a developer. The general expectation is that
a prefix extends the functionality. 

The configurations: ``cordapp`` and ``cordaRuntime`` are in effect only useful for the `cordformation` plugin. This is a bit surprising and non-intuitive.

Even without the new requirements, in my subjective opinion for a normal cordapp developer, this is not the most straight-forward way to define   


#### Cordapp Metadata / Versioning and the Contracts/Workflows split
  
The original plugin was created before the versioning requirements and the need to develop Corda SDKs. 
The requirement to have separate `contracts`/`flows` modules was also not evident at that time.
 
These requirements were implemented in Corda 4 by creating 2 new configuration blocks: ``contract`` or ``workflow``  where the versioning metadata is added. 
Based on what block is added to the build file one of ``Cordapp-Contract-Licence`` or``Cordapp-Workflow-Licence`` (or similar ``*-Name`` , ``*-Vendor``, ``*-Version``)
is added to the manifest file. 


Currently these are set in ``contract`` /``workflow`` specific blocks

```groovy
    contract {
        name "Corda IRS Demo"
        versionId 1
        vendor "R3"
        licence "Open Source (Apache 2)"
    }
```

or

```groovy
    workflow {
        name "Corda IRS Demo"
        versionId 1
        vendor "R3"
        licence "Open Source (Apache 2)"
    }
```

A general utility app that does not fit in one of these categories does not have a way to specify these.  


#### Set the `targetPlatformVersion`/`minimumPlatformVersion` and other metadata.

Currently these are set in the ``cordapp`` or ``cordapp.info`` block:  

```groovy
cordapp {
    info {
        name "Trader Demo"
        vendor "R3"
        targetPlatformVersion corda_platform_version.toInteger()
        minimumPlatformVersion 1
    }
}
```

(Not sure about the reasoning.)


#### Declare explicit dependencies to corda core and test artifacts.

This sort of code is pretty much redundant in all cordapps:

```groovy
    // Corda integration dependencies
    cordaCompile "$corda_release_group:corda-core:$corda_release_version"
    cordaCompile "$corda_release_group:corda-finance:$corda_release_version"
    cordaCompile "$corda_release_group:corda-jackson:$corda_release_version"
    cordaCompile "$corda_release_group:corda-rpc:$corda_release_version"
    cordaCompile "$corda_release_group:corda-node-api:$corda_release_version"
    cordaRuntime "$corda_release_group:corda:$corda_release_version"
```

Note that all these dependencies use the ``corda`` prefix.

This could also be achieved by using this android inspired format: 

```groovy
cordaCompilePlatformVersion distribution: "OS", version: "4.3"
```
(see https://developer.android.com/studio/build)


#### Driver tests 

A consequence of the current approach is that driver tests are started polluted with CorDapps that are not on the system classpath on a standalone node.

Also, because there is no integration with the driver, functions like `findPackages` and `scanPackages` are required when writing a test because the
driver needs to have a folder with JARs to deploy. 

Currently `findPackages` triggers a gradle build from inside the test to get the actual jar, while `scanPackages` builds an ad-hoc temporary jar. 

Another factor is that the driver test / CorDapp plugin framework was designed with both the platform and Cordapp developers in mind. But the requirements
differ in the 2 use cases.

For example: ``scanPackages`` does not make much sense to be used for a CorDapp developer.  


#### Cordformation

A shortcoming of this approach - which could be considered a bug - is the clunky integration with the ``cordformation`` plugin.

E.g.:  https://github.com/corda/cordapp-template-kotlin/blob/release-V4/build.gradle

Notice that in order to use the ``cordformation`` plugin the root project must declare a dummy ``cordapp``.  


#### Sign the output jar.

The Cordapp output jar must be signed using the jarsigner. This is required if the jar is to be used with the ``SignatureConstraint``

Currently this is done with a signing block like this.

```groovy
    cordapp {
        signing {
            enabled true
            options {
                keystore "/path/to/jarSignKeystore.p12"
                alias "cordapp-signer"
                storepass "secret1!"
                keypass "secret1!"
                storetype "PKCS12"
            }
        }
        //...
    }
``` 

The requirements are:

- During development and testing the jar must be signed with a development key. This could be the standard corda dev key. 
- When preparing for production, signing must be performed with externally provided configurations. (e.g: maybe HSM). 

The main issue with this plugin is that the ``signing`` block is named identical to the standard gradle signing plugin: https://docs.gradle.org/current/userguide/signing_plugin.html.
This might confuse people who wish to use it to publish to public repositories.

  
#### Publishing  

The custom corda publishing plugin publishes the main jar. 



## (New) High level requirements for CorDapp projects

At this point, these are the types of code that someone can develop:

1. Smart contracts
2. Workflows
3. Corda utilities
4. RPC client

The first 3 have in common the fact that they run inside a Corda node. We will discuss only them.
The RPC client code (4.) is just a plain java gradle module that depends on a normal library. It does not run inside Corda.

The ``3. Corda utilities`` use case was not a requirement initially. 


#### Integrate nicely with the ``cordformation`` plugin and the ``driver``.

From the point of view of a CorDapp developer both ``cordformation`` and the ``driver`` tests are different ways of testing the CorDapp they're writing.
 
It should require as little boilerplate as possible, have sensible defaults and allow testing various combinations of dependencies and versions.


#### Declare dependencies.

Since the development of the original CorDapp plugin, gradle has released a new ``java library plugin`` : https://docs.gradle.org/current/userguide/java_library_plugin.html.
This contains a number of useful features that might benefit CorDapp developers.

Contract code (aka ``on ledger code``) must export cryptographic links to its dependencies which will be used to ensure contract verification code is valid. 
 

## CorDapp Classloader isolation. 

Classloader isolation is potential future work that must be designed and implemented in the Corda node.
That decision will affect how to define dependencies and package cordapps. 

There's 2 types of CorDapp code with different security characteristics:


##### 1. On ledger code (states and contracts) 
This code must prove that it is valid by a cryptographic chain from the root of trust - which is the contract constraint of the states. 

A transaction can mix multiple contracts that must be able to inter-operate.
Currently a classloader per transaction is created, which contains all contracts and all their dependencies. 
See https://docs.corda.net/head/cordapp-advanced-concepts.html and  https://groups.io/g/corda-dev/topic/31936558?p=Created,,,20,1,0,0::recentpostdate/sticky,,,20,2,0,31936558

When contract jars contain cryptographic links to dependencies, a CorDapp classloader can be created which isolates Cordapps from each other, and avoids all the pitfalls of the current approach.


##### 2. Workflow code.  
This code is installed on the node by an administrator, so it doesn't need any cryptographic proof. 
This is code similar to applications deployed on an application server.

Note: ``On ledger code`` will also run in this context. 

Currently we deploy all CorDapp jars into a folder and create one classloader from all of them.
This is not really scalable on the long term.

Issues:
 - Multiple CorDapps could depend on different versions of a dependency. This could lead to unexpected behaviour.
 - It does not support installing multiple versions at the same time, to avoid the draining mode.
 - Running all CorDapps in a single classloader prevents isolating the actual data from other CorDapps. 



#### Integrate nicely with the ``cordformation`` plugin and the ``driver``.

From the point of view of a CorDapp developer both ``cordformation`` and the ``driver`` tests are different ways of testing the CorDapp they're writing.
 
It should require as little boilerplate as possible, have sensible defaults and allow testing various combinations of dependencies and versions.

