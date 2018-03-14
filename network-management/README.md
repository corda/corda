
# Building the binaries

## Network management server
To build a fat jar containing all the doorman code you can simply invoke:
```
    ./gradlew network-management:capsule:buildDoormanJAR
```

The built file will appear in:
```
network-management/capsule/build/libs/doorman-<version>.jar
```
## HSM signing server
To build a fat jar containing all the HSM signing server code you can simply invoke:
```
    ./gradlew network-management:capsule-hsm:buildHsmJAR
```

The built file will appear in:
```
network-management/capsule-hsm/build/libs/hsm-<version>.jar
```

The binaries can also be obtained from artifactory after deployment in TeamCity.

To run the HSM signing server:

```
cd network-management
java -jar capsule-hsm/build/libs/hsm-<version>.jar --config-file hsm.conf
```

For a list of options the HSM signing server takes, run with the `--help` option:
```
java -jar capsule-hsm/build/libs/hsm-<version>.jar --help
```

## HSM Certificate Generator

To build a fat jar containing all the hsm certificate generator code you can simply invoke
```
    ./gradlew network-management:capsule-hsm-cert-generator:buildHsmCertGeneratorJAR
```

The built file will appear in
```
network-management/capsule-hsm-cert-generator/build/libs/hsm-cert-generator-<VERSION>.jar
```

#Configuring network management service
### Local signing

   When `keystorePath` is provided in the config file, a signer will be created to handle all the signing periodically using the CA keys in the provided keystore.
   
   The network management service can be started without a signer, the signing will be delegated to external process (e.g. HSM) connecting to the same database, the server will poll the database periodically for newly signed data and update the statuses accordingly.
   
   Additional configuration needed for local signer:
   ```
   #For local signing
   rootStorePath = ${basedir}"/certificates/rootstore.jks"
   keystorePath = ${basedir}"/certificates/caKeystore.jks"
   keystorePassword = "password"
   caPrivateKeyPassword = "password"
   ```

## Doorman Service
Doorman service can be started with the following options :

### JIRA

The doorman service can use JIRA to manage the certificate signing request approval workflow. This can be turned on by providing JIRA connection configuration in the config file.
   ```
   doorman {
       jira {
         address = "https://doorman-jira-host.com/"
         projectCode = "TD"
         username = "username"
         password = "password"
       }
       .
       .
       .
   }
   ```
#### JIRA project configuration
* The JIRA project should setup as "Business Project" with "Task" workflow.
* Custom text field input "Request ID", and "Reject Reason" should be created in JIRA, doorman will exit with error without these custom fields.
  
### Auto approval 
  When `approveAll` is set to `true`, the doorman will approve all requests on receive. (*This should only be enabled in a test environment)
    
### Network map service
  Network map service can be enabled by providing the following config:
  ```
  networkMap {
    cacheTimeout = 600000
    signInterval = 10000
  }
  ```
  `cacheTimeout`(ms) indicates how often the network map should poll the database for a newly signed network map. This is also added to the HTTP response header to set the node's network map update frequency.  
  `signInterval`(ms) this is only relevant when local signer is enabled. The signer poll the database according to the `signInterval`, and create a new network map if the collection of node info hashes is different from the current network map. 
    
##Example config file
```
basedir = "."
address = "localhost:0"
rootStorePath = ${basedir}"/certificates/rootstore.jks"
keystorePath = ${basedir}"/certificates/caKeystore.jks"
#keystorePassword = "password" #Optional if not specified, user will be prompted on the console.
#caPrivateKeyPassword = "password" #Optional if not specified, user will be prompted on the console.
#rootPrivateKeyPassword = "password" #Optional if not specified, user will be prompted on the console.
#rootKeystorePassword = "password" #Optional if not specified, user will be prompted on the console.

dataSourceProperties {
  dataSourceClassName = org.h2.jdbcx.JdbcDataSource
  "dataSource.url" = "jdbc:h2:file:"${basedir}"/persistence;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=0;AUTO_SERVER_PORT="${h2port}
  "dataSource.user" = sa
  "dataSource.password" = ""
}

database {
    runMigration = true
}

h2port = 0

# Comment out this section if running without doorman service
doorman {
  approveInterval = 10000
  approveAll = false
  jira {
    address = "https://doorman-jira-host.com/"
    projectCode = "TD"
    username = "username"
    password = "password"
  }
}

# Comment out this section if running without network map service
networkMap {
  cacheTimeout = 600000
  signInterval = 10000
}
```

# Running the network

### 1. Create keystore for local signer

   If local signer is enabled, the server will look for key stores in the certificate folder on start up.
   The key stores can be created using `--mode` flag.
   ```
   java -jar doorman-<version>.jar --config-file <config file> --mode ROOT_KEYGEN
   ```
   and 
   ```
   java -jar doorman-<version>.jar --config-file <config file> --mode CA_KEYGEN
   ```
   
   A trust store containing the root certificate will be created in the location `distribute-nodes / network-root-truststore.jks`
   (relative to `rootStorePath`). The trust store's password can be set using command line argument `--trust-store-password`, 
   or the doorman's keygen utility will ask for password input if trust store password is not provided using this flag.
   This trust store file is to be distributed to every node that wishes to register with the doorman. The node cannot
   register without it.

### 2. Start Doorman service for notary registration 
   Start the network management server with the doorman service for initial bootstrapping. Network map service (`networkMap`)
   should be **disabled** at this point. **Comment out** network map config in the config file and start the server by running :
   ```
   java -jar doorman-<version>.jar --config-file <config file>
   ```
   
### 3. Create notary node and register with the doorman
   After the doorman service is started, start the notary node for registration.
   ```
   java -jar corda.jar --initial-registration --network-root-truststore-password <trust store password>
   ```
   By default it will expect trust store file received from the doorman to be in the location ``certificates/network-root-truststore.jks``.
   This can be overridden with the additional `--network-root-truststore` flag.
   
   NOTE: This step applies to all nodes that wish to register with the doorman.

### 4. Generate node info files for notary nodes
   Once notary nodes are registered, run the notary nodes with the `just-generate-node-info` flag.
   This will generate the node info files, which then should be referenced in the network parameters configuration.

### 5. Add notary identities to the network parameters
   The network parameters should contain reference to the notaries node info files.
      Example network parameters file:
      
      notaries : [
          {
              notaryNodeInfoFile: "/Path/To/NodeInfo/File1"
              validating: true
          },
          {
              notaryNodeInfoFile: "/Path/To/NodeInfo/File2"
              validating: false
          }
      ]
      minimumPlatformVersion = 1
      maxMessageSize = 10485760
      maxTransactionSize = 10485760
      
   Save the parameters to `network-parameters.conf`

### 6. Load initial network parameters file for network map service
A network parameters file is required to start the network map service for the first time. The initial network parameters file can be loaded using the `--update-network-parameters` flag.
We can now restart the network management server with both doorman and network map service.  
```
java -jar doorman-<version>.jar --config-file <config file> --update-network-parameters network-parameters.conf
```

### 7. Logs
In order to set the desired logging level the system properties need to be used.
Appropriate system properties can be set at the execution time.
Example:
```
java -DdefaultLogLevel=TRACE -DconsoleLogLevel=TRACE -jar doorman-<version>.jar --config-file <config file>
```