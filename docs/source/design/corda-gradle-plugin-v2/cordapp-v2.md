# CorDapp next generation plugin - Proposed solution

The overall goal is to improve the current solution by cleaning up the concepts and implementing the new requirements.

### Sample config 

The proposed solution is best explained by a sample.
 
```groovy
    apply plugin: 'net.corda.plugins.cordapp'

    cordapp {        
        // If this code is designed to be saved as code-on-ledger. E.g.: states and contracts.
        // If this is set to true:
        //  - It will autoinclude the `dependency-constraints` plugin.
        //  - It will also include the `jar-signer` plugin and sign the JAR.
        //  - It will set:   `` cordaComponents = ["corda-core"]`` 
        // Could this be used for other purposes - like DJVM stuff? 
        onLedgerCode true
  
        // Same as before but moved from the contract or workflow blocks.
        name "Foo contract"
        versionId 2
        vendor "MegaCorp"
        licence "Great licence"
      
        // Same as before. 
        minimumPlatformVersion 3
        targetPlatformVersion 4
    }
    
    // If `onLedgerCode=true` or the `dependency-constraints` plugin was included, all these dependencies
    // will by default be pinned down in the output JAR of this CorDapp.
    dependencies {
        implementation "com.foo:foo:1.2"
        api "com.bar:bar:2.1"
        implementation project("useful-lib")
        // more dependencies
    }
```

As can be seen, the ``cordapp`` block and most of the syntax is similar to the legacy plugin to make migration easy.

This gradle snippet must be added to the module containing cordapp code.



## How it works

The ``cordapp`` plugin depends on the ``corda`` plugin .

It must be applied *only* to modules that contain code that will be loaded by Corda.
 
Its responsibilities are:
    - to set the required metadata (e.g.: ``targetPlatformVersion``, etc).
    - verify that the metadata is compatible to the cordaCompilePlatformVersion
    - based on the ``onLedgerCode`` setting 
        - to include the ``dependency-constraints`` and add cryptographic proofs to dependencies.
        - to include the `jar-signer` plugin and sign the JAR.
        - to configure the components or Corda that are required.
              


## Changes from the current version.

### Packaging

This is the area where the changes are the most important.


#### On ledger CorDapps 

Also, know as "contract JARs", these will no longer be distributed by default as semi fat-jars, but as normal jars with cryptographic links to their dependencies.

The plugin developer is still free to create a fat-jar, but must use a custom mechanism for that - like the shadow plugin. 
 
Complexity introduced by shading:
    - if the contract only shades code that is used by its internal verification logic, then shading should have no implications. 
    Basically shading an implementation library is ok. 
    - if the shaded dependency is used as part of a data that gets serialized with the state, then the shaded dependency must ripple through to the 
    modules that use that jar. ( This is equivalent to shading an ``api`` dependency)
    Flows must depend on the  'shadow' configuration of the contracts jar.


####  Application ( workflows) CorDapps 

These CorDapps must be deployed by an administrator on the node.

Currently, all installed CorDapps are loaded in the same classloader, but this will change when CorDapps will be isolated.

There is no real need for cryptographic links to dependencies in this case.  They can be just normal artifacts published to a maven repository together with a ``pom`` file that describes the libraries it depends upon.


#### How to distribute CorDapps - potential solution

Cordapps will be distributed as maven coordinates.
There should be a command line utility ``install -cordapp com.foo:foo-cordapp:1.0 -repository ... -user -password``.
This would create a sub-folder ``cordapps/com.foo:foo-cordapp:1.0`` where it will download the main CorDapp and all its dependencies. 
Note that this type of structure could support multiple simultaneous versions, because a self-contained classloader can be created from each of these folders.
Checkpoints will point to the version of the folder. 

Contract jars will be installed in one of these CorDapp folders, as they have no 

This difference in packaging will have implications in how CorDapps will be installed and loaded.
TODO - elaborate on this.  - separate design?

Given that the output is not a fat-jar any more, all custom ``cordaFoo`` configurations are now removed.


### Testing

All testing responsibilities are extracted into a ``corda-testing`` plugin that will integrate with the driver and ``CordFormation``.
Cordformation or driver tests must live in a different module, as they are integration style tests that are consumers of the CorDapp.  


### Move versioning and license metadata to the main block. 

All sub-blocks like ``contract``, ``workflow``, ``info`` will be removed and the metadata must be declared in the main block.

The advantage is that it's more straight forward.

Note: this will have implications on the coe that is loading this metadata. 


### Typical migration

1. Apply the new plugin id instead of the old one.
2. Replace the explicit Corda dependencies with ```compilePlatformVersion```. 
3. Set ```onLedgerCode``` if this is contract code.          
4. Move all the metadata to the root ``cordapp``.
3. Remove all the driver testing and code formation into a separate module.  

#### Backwards compatibility

- read metadata from both old and new versions
- If ``targetPlatformVersion < 5``, the plugin will add the jar as a dependency and provide a snippet of code to be added. 


TODO - dependency - exclude all stuff that is a transitive dep of corda