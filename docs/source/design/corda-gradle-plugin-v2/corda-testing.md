# Cordapp Testing 

## Background 

Testing a CorDapp can be done in multiple ways:

1. Unit testing the logic without starting any node.
2. ``MockNetwork`` "unit" testing.
3. ``Driver`` in-process testing
4. ``Driver`` out-of-process testing.
5. ``Cordformation`` - starting full local nodes for manual testing. 

Note: Starting nodes in containers is out of scope.

The test classpath should be different when testing 1. or 3.:  *When running a driver test, the classpath of the test is conceptually an rpc client*.

Also, starting in-process driver nodes should be done over a classpath containing only the corda node.
The lambda passed to the driver should be executed in the context of a child classloader.

Building and retrieving dependencies should be the responsibility of gradle and they should be made available for tests. Maybe in a file that 
can be passed as a system parameter.

Question: Currently we decide if nodes should be started in-process or out-of-process when initializing the driver. 
Doesn't this setting belong to ``startNode`` with a default in the driver setup?


Note: This proposal is intended just for CorDapps developers. 
Driver tests that we (R3 devs) write to test the platform will have to use a different approach to discover which apps to deploy.   


## Requirements

- Make the driver tests match standalone nodes when it comes to classloading. The system classpath of the node should only contain the corda node. 
    
- Allow driver tests and cordformation nodes to deploy multiple versions of CorDapps that can be retrieved either from the current project or from a maven repo.

- Define named combinations of CorDapp libraries that can be referenced from both the driver and cordformation. There should be a default
one called ``"currentProjectCordapps"`` that will be loaded by default containing all CorDapp dependencies from the current project. 

- Nice to have - start the driver nodes with multiple versions of Corda.

- A less verbose, more declarative ``cordformation`` successor able to use the new features. The multi-version networks defined this way could be used in automated tests.   

- Export dependency and classpath metadata from the gradle build into a file that can be read by driver tests 


## Proposed solution 

A new ``cordapp-test`` plugin that will extract useful information from the ``corda`` and the ``cordapp`` plugins.
From the ``corda`` plugin it will extract the information to start clean nodes. From the ``cordapp`` plugins it will discover which CorDapps it must deploy.
 
It will also require changes to the driver framework to make use of the metadata exposed by the new plugin.

The testing plugin detects when it is used in the same project as a CorDapp and must throw an error with an appropriate message.


### How to reference multiple versions of a CorDapp to use them in tests?

#### Option 1 - Reference CorDapps directly

Based on the test dependencies of the current module, the plugin can detect which ones are ``CorDapps``. 
For each of these it will export all versions into a structure that can be accessed from cordformation and driver tests.

By default, tests will deploy the current versions of all CorDapp dependencies.
 
The CorDapps to deploy can be customized as follows.

From the driver tests:

```kotlin
    driver {
        startNode(providedName = DUMMY_BANK_B_NAME ) // not specifying anything will deploy the current versions of the Cordapps the projects depends upon.
        startNode(providedName = DUMMY_BANK_A_NAME, deployCordapps = listOf("com.foo:foo-workflow:2.1", "com.bar:bar-workflow:3.7"))
        // ..
    } 
```

#### Option 2 - Create Profiles

A profile is a named combination of CorDapps. 
It can be a historical version of the CorDapp defined in the current project. 

By default, an implicit ``currentProjectCordapps`` is created from the current ``cordapp`` modules that the project depends on.

```groovy
    apply plugin: 'net.corda.plugins.test-cordapp'

    cordapp-testing {
    
        profiles {
            // these profiles can be used by cordformation and driver tests.
            "foo2.1_bar3.7"  {
                deploy "com.foo:foo-workflow:2.1"            
                deploy "com.bar:bar-workflow:3.7"            
            }      

            "foo2.1_current_bar"  {
                deploy "com.foo:foo-workflow:2.1"            
                deploy project("bar-workflow")            
            }      
        }
    }
```

From the driver tests, this is how one can refer these profiles:

```kotlin
    driver {
        startNode(providedName = DUMMY_BANK_A_NAME, cordappProfile = "foo2.1_bar3.7")
        startNode(providedName = DUMMY_BANK_B_NAME ) // not specifying anything will deploy the `currentProjectCordapps`
        // ..
    } 
```

Note that, for a new project the implicit `currentProjectCordapps` will bind everything together.

This option works better when testing CorDapps that depend on other CorDapps, as it can make the combinations more explicit. 


Option 1 is chosen.


## Cordformation Plugin

#### Current format:

```groovy
task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
    nodeDefaults {
        projectCordapp {
            deploy = false
        }
        cordapp project(':contracts')
        cordapp project(':workflows')
    }
    node {
        name "O=Notary,L=London,C=GB"
        notary = [validating : false]
        p2pPort 10002
        rpcSettings {
            address("localhost:10003")
            adminAddress("localhost:10043")
        }
    }
    node {
        name "O=PartyA,L=London,C=GB"
        p2pPort 10005
        rpcSettings {
            address("localhost:10006")
            adminAddress("localhost:10046")
        }
        rpcUsers = [[ user: "user1", "password": "test", "permissions": ["ALL"]]]
    }
    node {
        name "O=PartyB,L=New York,C=US"
        p2pPort 10008
        rpcSettings {
            address("localhost:10009")
            adminAddress("localhost:10049")
        }
        rpcUsers = [[ user: "user1", "password": "test", "permissions": ["ALL"]]]
    }
}
```

It is quite verbose, with a lot of redundancy.
For example, note the ```projectCordapp.deploy = false```, which is a workaround for the fact that one must import the ``cordapp`` plugin in the same project to use the ``cordapp`` configuration.


#### Proposed format:

There will be embedded default values for all properties.
All the ports are replaced with port generators similar to the driver tests. This avoids a lot of verbosity.

The concept of ``network`` is added. Due to reduced verbosity, named networks can be defined, and used from gradle ``deployNetwork1`` tasks or from driver tests.  

The name of the node can replace the redundant ``node``.

By default, the current cordapps will be deployed, but it can be customized by referring previous versions from the structure mentioned above.  

```groovy
    cordformation {
        
        // This removes the need to manually set ports for each node.
        // These are the default values embedded in the plugin. These lines are not required. 
        rpcPortAllocation = INCREMENTAL(9000) 
        p2pPortAllocation = INCREMENTAL(10000) 
        rpcAdminPortAllocation = INCREMENTAL(11000) 
            
        nodeDefaults {
            // This is the default embedded in the plugin. It is not necessary to add this line
            rpc.user = ['username': "demo", 'password': "demo", 'permissions': ["ALL"]]
        }
    
        // Name of the network. The default one will just be ``deployNodes``
        defaultNetwork {
            "O=Notary Service,L=Zurich,C=CH" {
                notary = [validating : true]
                // Note that not specifying anything will deploy the current Project Cordapps
            }
    
            "O=Bank A,L=Zurich,C=CH" {
                cordapps = ["com.foo:foo-workflow:2.1", "com.bar:bar-workflow:3.7"]
            }
           
            "O=Bank B,L=London,C=UK" 
            // more nodes..
        }       
        
        // more networks..
        bankNetwork {
            "O=Notary Service,L=Zurich,C=CH" {
                notary = [validating : false]
            }
   
            (1..10).forEach{
                "O=Bank ${it},L=Zurich,C=CH" 
            }
        }       
    }
```

The plugin will create a ``deploy`` task for each network. e.g.: ``deployBankNetwork``

Note: The network descriptions can also be used by the driver tests, to optimize tests.
Multiple tests can use the same network without tearing it down.
The limitation is that tests must assume they don't run on an empty database.
But nothing should stop a test from creating a new fresh node and adding it to the network and then only tearing that down.
 

## Changes to the Driver framework

#### Api: 
A new ``deployCordapps`` property must be added to ``DriverParameters`` and to ``startNode``.
This will be mutually exclusive and will deprecate ``findCorDapp`` and ``extraCordappPackagesToScan``.

#### Implementation:
Gradle creates the testing metadata info and the right classpath to start the test in.

The driver will then create a new Classloader in which it will run all the driver logic.
For in-process nodes, it will start all nodes over the clean Corda classpath.
For out-of-process nodes it will use the classpath from the metadata file


## Testing metadata 

A file named: "corda-testing.yaml", with the following structure:

```yaml
NODE_CLASSPATH: the_entire_corda_classpath_required_to_start_a_node

DRIVER_CLASSPATH: the_classpath_of_the_driver_code

CORDAPPS:
  - "com.foo:foo-workflow:": list_of_jars      
  - "com.foo:foo-workflow:1.5": list_of_jars      
  - "com.foo:foo-workflow:2.1": list_of_jars      
  - "com.foo:foo-workflow:2.2": list_of_jars      
  - "com.bar:bar-workflow:": list_of_jars      
  - "com.bar:bar-workflow:3.7": list_of_jars      
```

TODO - describe how to export the networks.


## Backwards compatibility

The new plugins are opt-in only. The developer will have to make an active effort to transition to the new format. 
This migration will come with all the described benefits.

The driver must continue to work with the old plugin in case it is not started with the new metadata.


## Migration (for CorDapp developers)

1. If driver tests were defined in one of the ``cordapp`` modules, create a new module for driver tests and move them there. Or move them to the root module.
2. Add normal dependencies like: ``testImplementation`` to the modules containing the cordapps.
3. Modify the ``cordformation`` tasks. This should be straight forward.
4. Remove the ``findCordapps``/``scanPackages`` from the driver tests. At this point, it should work as before with the default ``currentProjectCordapps``. 
5. Add advanced features, like deploying previous cordapps.

Note: Due to gradle peculiarities, this design is very likely not 100% accurate.


## Phase 2 - test with multiple Corda versions

The ``corda-plugin`` knows about all existing versions of Corda and how components map to artifacts.
The test plugin will extract this information and expos it in a ``CORDA_VERSIONS`` map.

```yaml
DRIVER_CLASSPATH: the_classpath_of_the_driver_code

CORDAPPS:
  - "com.foo:foo-workflow:": list_of_jars      
  - "com.foo:foo-workflow:1.5": list_of_jars      
  - "com.foo:foo-workflow:2.1": list_of_jars      
  - "com.foo:foo-workflow:2.2": list_of_jars      
  - "com.bar:bar-workflow:": list_of_jars      
  - "com.bar:bar-workflow:3.7": list_of_jars      

CORDA_VERSIONS:
    - "OS:3.2": list_of_jars
    - "OS:4.1": list_of_jars
    - "ENT:3.3": list_of_jars
    - "ENT:4.2": list_of_jars

NODE_CLASSPATH: "ENT:4.2" // the current project version 
```

```kotlin
    driver {
        startNode(providedName = DUMMY_BANK_A_NAME, corda = "ENT:3.3", deployCordapps = listOf("com.foo:foo-workflow:2.1", "com.bar:bar-workflow:3.7"))
        // ..
    } 
```

```groovy
    cordformation {        
        // ..
    
        // Name of the network
        bankNetwork {
            "O=Bank A,L=Zurich,C=CH" {
                cordapps = ["foo2.1_bar3.7"]
                corda "OS:3.2"
            }
           
            "O=Bank B,L=London,C=UK" 
            // more nodes..
        }       
    }
```
