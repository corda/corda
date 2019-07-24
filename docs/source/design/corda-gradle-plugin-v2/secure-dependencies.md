# Secure dependencies 

Code that is run on the ledger needs a cryptographic chain of proof to a root of trust.

This proposes a solution based on:
1. Gradle plugin that adds a list in the output jar containing the maven coordinate and the cryptographic hash of each dependency. 
2. A library that reads and enforces this metadata.
   
Note: This mechanism is not designed to protect against dependency-chain attacks.   

This plugin will be used transparently by the ``cordapp-v2`` plugin.


## 1. Gradle plugin

### Sample config 

```groovy
    apply plugin: 'net.corda.plugins.dependency-constraints'

    dependencyConstraints {
        
        // The gradle configurations to include. There will be sensible default values.
        includeConfigurations = ['api', 'implementation', 'runtime' ]
            
        // This approach is inspired by the shadow plugin: https://imperceptiblethoughts.com/shadow/configuration/dependencies/#filtering-dependencies.
        dependencies {
            include(dependency('com.foo:foo:1.0'))
            exclude(dependency(':log4j-core:2.11.1'))
            exclude(project(':useful-lib'))
            exclude(dependency {
                it.moduleGroup == 'org.apache.logging.log4j'
            })
        }       
    }   
```

### Description

The plugin will create a task: ``generateDependencyConstraints`` that will generate a file ``META-INF/DEPENDENCY-CONSTRAINTS``.
 
This will contain the SHA256 of all direct and transitive dependencies from the included configurations excluding the explicit ``exclude`` files.
This task will be wired to be executed before the ``jar`` plugin.

It can also be added to the source repository, so that any dependency changes become apparent during code review. 

Question: - Should this information be added to the ``MANIFEST`` file.
 
#### Format of the file

```csv
"com.foo:foo:1.0",f4dcbd5381fca48671886266ce411bc054c339588ccd2d26fb91520d1835ca1a
"com.bar:bar:3.2",fa78bb964078f1fd9c4bfb351361159553e5d5549023fe4a5305c858ee5a7704
```

### Depending on another secured library

If a dependency does already contain a ``META-INF/DEPENDENCY-CONSTRAINTS`` file or is a module that applies this plugin
then its transitive dependencies will not be added and the assumption is that at runtime the library will use the metadata from that jar.
In case of conflict, a dependency present in the root file takes precedence over one decalared in a dependency. 

#### Conflict resolution

The plugin will run a check to see if there are any conflicts between secured dependencies. Multiple versions of the same library can be found in the dependency graph.
In case of conflict detection, the plugin will fail with a helpful message, and the user is expected to resolve the conflict.    
This will be available as task to be used during development.


## 2. Runtime library  

This library contains a (URL?)Classloader that is able to read the ``META-INF/DEPENDENCY-CONSTRAINTS`` of all the loaded JARs and enforce the constraints. 
Because the file names might not match the maven coordinates, the library must identify the files based on their hashes.

The Classloader will be constructed with a list of ``URLs`` and the hash(es) of the root(s) of trust. 

The classloader will fail if:
 - a jar is found that can not be linked back to the root.
 - multiple versions of a dependency were added. 


Note: If the classloader is initialized with multiple roots of trust, to protect against a malicious root of trust, the classloader can also enforce the no-overlap rule.
