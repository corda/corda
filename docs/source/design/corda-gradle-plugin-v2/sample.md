# Gradle files for a basic template

## Root module

Set the version of Corda.

```groovy
    allprojects {
        apply plugin: 'net.corda.plugins.corda'   
        cordaCompilePlatformVersion distribution: "OS", version: "4.3"
    }
```

## Contracts module

Note the ``onLedgerCode true`` - which will enable all required plugins with sensible defaults.
It will also restrict the: ``cordaComponents = ["corda-core"]``.

```groovy
    apply plugin: 'net.corda.plugins.cordapp'
    
    cordapp {        
        onLedgerCode true
  
        name "Template CorDapp"
        vendor "Corda Open Source"
        licence "Apache License, Version 2.0"
        versionId 1
      
        minimumPlatformVersion 3
        targetPlatformVersion 4
    }
```

## Workflow module

```groovy
    apply plugin: 'net.corda.plugins.cordapp'
    
    cordapp {        
        name "Template Flows"
        vendor "Corda Open Source"
        licence "Apache License, Version 2.0"
        versionId 1
      
        minimumPlatformVersion 3
        targetPlatformVersion 4
    }
```

## Rpc client module

```groovy
    cordaComponents = ["corda-rpc"]
    
    dependencies {
        api project(":contracts")
        api project(":workflows")
    }
```

## Testing module

This is the module where the Driver integration tests will be located.
It is also the project where cordFormation configurations live.

```groovy
    apply plugin: 'net.corda.plugins.test-cordapp'
    
    dependencies {
        // In order to run tests, this module must depend on the code. 
        testImplementation project(":contracts")
        testImplementation project(":workflows")
        testImplementation project(":clients")
    }

    cordformation {
        rpcPortAllocation = INCREMENTAL(9000) 
        p2pPortAllocation = INCREMENTAL(10000) 
            
        nodeDefaults {
            rpc.user = ['username': "demo", 'password': "demo", 'permissions': ["ALL"]]
        }

        smallBankNetwork {
            "O=Notary Service,L=Zurich,C=CH" {
                notary = [validating : true]
            }
    
            "O=Bank A,L=Zurich,C=CH" 
           
            "O=Bank B,L=London,C=UK" 

            "O=Bank C,L=New York,C=US" 
        }       

        largeBankNetwork {
            "O=Notary Service,L=Zurich,C=CH" {
                notary = [validating : true]
            }
   
            (1..10).forEach{
                "O=Bank ${it},L=Zurich,C=CH" 
            }
        }       
    }
```

