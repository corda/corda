# Re-design of the CorDapp gradle plugin


## Background 
 
##### The current CorDapp plugin 

The original CorDapp plugin was created before sophisticated library dependency and packaging requirements became apparent.

To keep it simple under those requirements, its approach is to extend the functionality of the standard java plugin by giving extra packaging responsibilities to the basic dependency
configurations. (See https://docs.gradle.org/current/userguide/java_plugin.html and the newer https://docs.gradle.org/current/userguide/java_library_plugin.html)

These are the current configurations and what they mean:

- ``compile`` : both compile against *and fat-jar* this library.
- ``runtime`` : only fat-jar it - does not compile against it.
- ``cordaCompile`` : compile against this, but don't fat-jar.
- ``cordaRuntime`` : the `corda` prefix is preventing the library to be fat-jarred, and `runtime` to compile against. These libraries are used during tests and by the ``cordFormation`` plugin.
- ``cordapp`` : compile against it, but don't fat-jar and also deploy it to ``cordformation`` nodes.
 
As can be seen from the descriptions, the configuration names don't help too much in explaining their behaviour.  

The plugin also adds new configurations: ``cordapp`` and ``cordaRuntime`` that are only useful for the `cordformation` plugin. This is a bit surprising and non-intuitive.

Even without the new requirements, in my subjective opinion a normal gradle user does not expect ``compile`` to also package the dependency.



##### Driver tests 

Another shortcoming of this approach is that driver tests are started polluted with CorDapps that are not on the system classpath on a standalone node.

If it was integrated with the driver, functions like `findPackages` and `scanPackages` would no longer be required.

The driver test / CorDapp plugin framework was designed with both the platform and Cordapp developers in mind. But the requirements
differ in the 2 use cases.

For example: ``scanPackages`` does not make much sense to be used for a CorDapp developer.  


##### Versioning and the Contracts/Workflows split
  
The original plugin was created before the versioning requirements and the need to develop Corda SDKs. 
The requirement to have a separate `contracts` module was also not evident at that time.
 
These requirements were implemented by creating 2 new configuration blocks: ``contract`` or ``workflow``  where the versioning metadata is added. 
Based on what block is added to the build file one of ``Cordapp-Contract-Licence`` or``Cordapp-Workflow-Licence`` (or similar ``*-Name`` , ``*-Vendor``, ``*-Version``)
is added to the manifest file. 

I'm personally not quite sure what the reason was for creating different metadata for contracts and workflows.

As a matter of fact, there are standard java attributes that can be used for this purpose, like: ``Implementation-Title``. See: https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
Question: Should this metadata logic be revisited?


##### Cordformation

Another shortcoming is the clunky integration with the ``cordformation`` plugin.

E.g.:  https://github.com/corda/cordapp-template-kotlin/blob/release-V4/build.gradle

Notice that in order to use the ``cordformation`` plugin the root project must declare a dummy ``cordapp``.  

  
##### Other areas that can be improved:

- It requires to declare explicit dependencies to corda core and test artifacts. 
- 



## Requirements

At this point, these are the types of code that someone can develop:

1. Smart contracts
2. Workflows
3. Corda utilities
4. RPC client

The first 3 have in common the fact that they run inside a Corda node. We will discuss only them.
The RPC client code (4.) is just a plain java gradle module that depends on a normal library. It does not run inside Corda.

The goal is for a `CorDapp` module to be a standard `java`/`kotlin` module that will output one or multiple artifacts.
The requirement is to choose the least surprising approach, which means that existing functionality should work as it works in the standard plugin.   
The plugin should only add new configurations for Corda specific problems.


#### Set the versions of Corda that the code must compile and be tested against. 

Currently this is done using plain dependencies. 
CorDapps can depend on either ENT or OS.


#### Set the `targetPlatformVersion` and `minimumPlatformVersion` to the desired versions.

Given that the code runs inside Corda, these 2 versions must be set inside the manifest file.
Corda reads them using stack walking or directly from the manifest.

Currently these are set in the ``cordapp`` block:  

```groovy
cordapp {
    targetPlatformVersion = corda_platform_version.toInteger()
    minimumPlatformVersion 1
    // ...
}
```


#### Set other manifest metadata. 

These are required for licensing and UI purposes (and maybe metering).

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
Ideally we'd use standard java properties for them.


#### Declare CorDapp dependencies.

Assuming we aim to keep the default java plugin behaviour unchanged, each configuration should mean exactly what it means in the java plugin.
(see https://docs.gradle.org/current/userguide/java_library_plugin.html)
 
Besides the usual java/kotlin dependencies, the extra types of dependencies that CorDapps need are:

1. Dependency that must be added to the output fat-jar. 
2. Dependency that must be added to the output fat-jar - shaded under a different namespace. 
3. Dependency that must also be deployed but with cryptographic constraints - either hash or signature. 
The dependency constraint will end up in a `META-INF/MODULE-GRAPH-SECURITY` file in the output JAR. 

Other dependency types that are Corda specific but are only relevant for a "Corda testing plugin": 
- Dependency that must also be deployed on the node but without any security requirements. 
- Dependency that must be on the classpath of tests. Note: Should be different types.


#### Possibility to sign the output jar.

The output jar must be signed using the jarsigner. This is required if the jar is to be used with the ``SignatureConstraint``

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


#### Integrate nicely with the ``cordformation`` plugin and the ``driver``.

From the point of view of a CorDapp developer both ``cordformation`` and the ``driver`` tests are different ways of testing the CorDapp they're writing.
 
It should require as little boilerplate as possible, have sensible defaults and allow testing various combinations of dependencies and versions.
 
 

## Proposed solution

- Create a new plugin and deprecate the current one. The complexity of improving the existing plugin is too high. 
- Remove the need to explicitly add specific corda dependencies but add a new property: ``compilePlatformVersion``, that will indicate to the plugin how to include all the right libraries.
- Keep the ``cordapp`` block and most of the syntax similar to make migration easy.
- Don't extend the configurations of the java/kotlin plugin. This will reduce the surprise factor. 
- Make the packager plugable. It can default to the un-extended ``jar`` task, or if the ``shadow`` plugin is added to the project, default to that. 
Otherwise the user can specify any task they like for building the fat-jar. This approach has the big advantage that it gives the user maximum flexibility while 
keeping the complexity of the cordapp plugin low. For example the shadow plugin: https://imperceptiblethoughts.com/shadow/introduction/ is widely used and is very rich in features.    
- Add a section ``dependencyConstraints`` that can specify what cryptographic constraints the libraries from the same classloader must obey. 
These will be added to a `META-INF/MODULE-GRAPH-SECURITY` file after the packager has completed.
- There are no specific dependencies that are used for ``cordformation`` any more. The proposal is to add that logic to a new "cordapp-testing" plugin. See below for details. 

```groovy
    apply plugin: 'net.corda.plugins.application'
    
    cordapp {
        compilePlatformVersion distribution: "OS", version: "4.3"

        minPlatformVersion 3
        targetPlatformVersion 4
  
        name "Foo contract"
        versionId 2
        vendor "MegaCorp"
        licence "Great licence"

        // This allows each project to use whatever task it prefers to create the output jar
        // For example the very powerful shadow plugin that allows shading, filtering, minimizing, etc.
        packagerTask "shadow" 
        
        // Set the constraints
        dependencyConstraints {
            constrain groupId: "com.foo", artifactId: "foo", algorithm-name: "SHA-256", identifiers: ["ABCD...", "XYZT..." ]                                 
            constrain groupId: "com.bar", artifactId: "bar", algorithm-name: "SHA256withECDSA", identifiers: ["1234..."]                                 
        }
        
        signing {
            keystore "/path/to/jarSignKeystore.p12"
            alias "cordapp-signer"
            storepass "secret1!"
            keypass "secret1!"
            storetype "PKCS12"
        }
    }
    
    dependencies {
        // normal dependencies.
    
    }
    
    // Shadow logic
```

Note: If the module does not contain any driver or codeformation tests, the above should be enough. 


## Alternatives considered
  
TODO



# Cordapp Testing Plugin

## Background 

Testing a CorDapp can be done in multiple ways:

1. Unit testing the logic without starting any node.
2. ``MockNetwork`` "unit" testing.
3. ``Driver`` in-process testing
4. ``Driver`` out-of-process testing.
5. ``Cordformation`` - starting full local nodes for manual testing. 

Note: Starting nodes in containers is out of scope.

There are subtle differences between what should be on the test classpath when testing 1. or 3.: 
 - *When running a driver test, the classpath of the test is conceptually an rpc client*.

Building and retrieving dependencies should be the responsibility of gradle and they should be made available for tests. Maybe in a file that 
can be passed as a system parameter.

Also setting the classpath of the test.

Question: Currently we decide if nodes should be started in-process or out-of-process when initializing the driver. 
Doesn't this setting belong to ``startNode`` with a default in the driver setup?


## Requirements

- Make the driver tests match standalone nodes when it comes to classloading. 
    - Define custom configuration for driverTests? 
    - Define custom configuration for unit tests?
- Allow driver tests and cordformation nodes to deploy multiple versions of CorDapps that can be retrieved either from the current project or from a maven repo.
- Start the driver nodes with multiple versions of Corda (Nice to have)
- Move all the test dependencies logic into a single place where it will be exposed to test frameworks like the driver and codeformation. (even MockNetwork?)
- Define named combinations of CorDapp libraries that can be referenced from both the driver and cordformation. There should be a default
one called "currentProjectCordapps" that will be loaded by default containing all dependencies from the current project. 


## Proposed solution 

A new ``cordapp-test`` plugin that creates new ``sourceSets``, dependency configurations, tasks and configurations.


```groovy
    apply plugin: 'net.corda.plugins.test-cordapp'

    cordapp-testing {
    
        profiles{
            // an implicit currentProjectCordapps is created from the current modules that the project depends on

            // this combination can be used by cordformation and driver tests.
            olderCordapps  {
                deploy "com.foo:foo:2.1"            
                deploy "com.bar:bar:3.7"            
            }      
        }
    }
    
    dependencies {
        // This will add `bar` to the classpath of the driver test. But the driver nodes will not have it on the classpath
        driver "com.bar:bar:3.7" 
    }
```

From the driver tests, this is how one can refer these cordapps.

```kotlin
    driver {
        startNode(providedName = DUMMY_BANK_A_NAME, cordapps = "olderCordapps")
        startNode(providedName = DUMMY_BANK_B_NAME ) // not specifying anything will deploy the `currentProjectCordapps`
        // ..
    } 
```

Note that, for a new project the implicit `currentProjectCordapps` will bind everything together.


# Cordformation Plugin

Idea on how to make the cordformation plugin simpler:

```groovy
    cordformation {
        
        // something like this to get rid of the need to manually set port for each node. 
        portAllocation = INCREMENTAL 
            
        nodeDefaults {
            rpc.user = ['username': "demo", 'password': "demo", 'permissions': ["ALL"]]
        }
        
        "O=Notary Service,L=Zurich,C=CH" {
            notary = [validating : true]
            // Note that not specifying anything will deploy the `currentProjectCordapps`
        }

        "O=Bank A,L=Zurich,C=CH" {
            cordapps = ["olderCordapps"]
        }
       
        // more nodes..
    }
```
