# Jar signing gradle plugin
 
The standard java process to sign a jar is to use the ``jarsigner`` tool. 
There is no reusable gradle plugin out there that does this. Most likely because signing jars with the ``jarsigner`` tool is not very popular these days (fell out of favour when applets disappeared).
Android also moved to a custom format: https://source.android.com/security/apksigning

But this is a reusable task that can be extracted into its own plugin, with the advantage that it could be used and maintained by the community.

The tool generally used for this is the Ant ``signJar`` task: https://ant.apache.org/manual/Tasks/signjar.html, which calls the standard java tools.

Our current approach is to embed this logic as part of the Cordapp plugin.

The main issue with our current approach is that the ``signing`` block is named identical to the standard gradle signing plugin: https://docs.gradle.org/current/userguide/signing_plugin.html.    

```groovy
    cordapp {
        //..

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


### Proposed solution

Extract the current code into its own plugin and make it a bit more general. Also rename it to ``signJar``

It should expose all the parameters that ant supports, and set some based on the current project (like the path of the jar)

```groovy
    apply plugin: 'net.corda.plugins.sign-jar'

    signJar {
        defaultProperties {
            keystore "/path/to/jarSignKeystore.p12"
            alias "cordapp-signer"
            storepass "secret1!"
            keypass "secret1!"
            storetype "PKCS12"
            // ... any other property supported by ant 
        }       
    }
```

Adding this plugin to a gradle module creates a new task ``signJar`` that would by default sign the primary artifact. It will be wired after ``jar``.

It supports default properties if nothing is passed in via command line arguments or explicitly set. 

The Cordapp plugin will set the default properties to the Corda dev key and will include this plugin for ``onLedgerCode``