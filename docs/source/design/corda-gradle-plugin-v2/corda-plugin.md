# Corda plugin

The proposal is to build a very simple plugin that removes the need to explicitly add specific corda dependencies. 
This should normally be added in the root module for all projects that depend on Corda.

It will add 2 properties: 
 - ``cordaCompilePlatformVersion`` for the version and distribution. This should be set once
 - ``cordaComponents`` will contain the components which the module needs. This should be set per module. E.g: ``["corda-core", "corda-node-api", "corda-rpc"]``

Because of the declarative approach, it also allows more flexibility, as the code could be compiled and tested against all corda versions between the ``minimumPlatformVersion`` and the ``cordaCompilePlatformVersion``.


### Sample config 
 
```groovy
    apply plugin: 'net.corda.plugins.corda'
    cordaCompilePlatformVersion distribution: "OS", version: "4.3"
    cordaComponents = ["corda-rpc"]
```

This will replace the current:

```groovy
    // Corda dependencies
    cordaCompile "$corda_release_group:corda-core:$corda_release_version"
    cordaCompile "$corda_release_group:corda-node-api:$corda_release_version"
    cordaRuntime "$corda_release_group:corda:$corda_release_version"
    cordaCompile "$corda_release_group:corda-finance:$corda_release_version"
    cordaCompile "$corda_release_group:corda-jackson:$corda_release_version"
    cordaCompile "$corda_release_group:corda-rpc:$corda_release_version"
```


If possible (based on the ``cordaCompilePlatformVersion``), this will also set the version of the other plugins like the ``cordapp`` and ``corda-testing`` plugins. 

Note: The other plugins: ``cordapp`` and ``corda-testing`` will depend on this plugin and will configure which components they need.
For example, ``onLedgerCode`` only needs a dependency to ``corda-core``.
