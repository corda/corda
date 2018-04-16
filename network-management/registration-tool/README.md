#Distributed Notary Registration Tool

The notary registration tool creates a CSR (Certificate Signing Request) with ``SERVICE_IDENTITY`` certificate role and sent to compatibility zone doorman for approval.
A keystore and a trust store will be created once the request is approved.  

##Configuration file
The tool creates the CSR using information provided by the config file, the path to the config file should be provided 
using the ``--config-file`` flag on start up.  

The config file should contain the following parameters.

```
Parameter                       Description
---------                       -----------
legalName                       Legal name of the requester. It can be in form of X.500 string or CordaX500Name in typesafe config object format.

email                           Requester's e-mail address.

compatibilityZoneURL            Compatibility zone URL.

networkRootTrustStorePath       Path to the network root trust store.

networkRootTrustStorePassword   Network root trust store password, to be provided by the network operator. Optional, the tool will prompt for password input if not provided. 

keyStorePassword                Generated keystore's password. Optional, the tool will prompt for password input if not provided.

trustStorePassword              Generated trust store's password. Optional, the tool will prompt for password input if not provided.
```

Example config file
```
legalName {
    organisationUnit = "R3 Corda"
    organisation = "R3 LTD"
    locality = "London"
    country = "GB"
}
# legalName = "C=GB, L=London, O=R3 LTD, OU=R3 Corda"
email = "test@email.com"
compatibilityZoneURL = "http://doorman.url.com"
networkRootTrustStorePath = "networkRootTrustStore.jks"

networkRootTrustStorePassword = "password"
keyStorePassword = "password"
trustStorePassword = "password"

```

##Running the registration tool

``java -jar registration-tool-<<version>>.jar --config-file <<config file path>>``

# Private key copy tool

The key copy tool copies the private key from the source keystore to the destination keystore, it's similar to the
``importkeystore`` option in Java keytool with extra support for Corda's key algorithms.  
**This is useful for provisioning keystores for distributed notaries.**

### Usage

``java -jar registration-tool-<<version>>.jar --importkeystore [options]``

### Example

Copy ``distributed-notary-private-key`` from distributed notaries keystore to node's keystore.

```
java -jar registration-tool-<<version>>.jar \
--importkeystore \
--srckeystore notaryKeystore.jks \
--destkeystore node.jks \
--srcstorepass notaryPassword \
--deststorepass nodepassword \
--srcalias distributed-notary-private-key
```

``--help`` prints a list of all the available options.
