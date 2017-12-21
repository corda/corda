
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
To build a fat jar containing all the HSM signer code you can simply invoke:
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
java -jar capsule-hsm/build/libs/hsm-<version>.jar --configFile hsm.conf
```

For a list of options the HSM signing server takes, run with the `--help` option:
```
java -jar capsule-hsm/build/libs/hsm-3.0-<version>.jar --help
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
   doormanConfig {
       jiraConfig {
         address = "https://doorman-jira-host.com/"
         projectCode = "TD"
         username = "username"
         password = "password"
         doneTransitionCode = 41
       }
       .
       .
       .
   }
   ```
  
### Auto approval 
  When `approveAll` is set to `true`, the doorman will approve all requests on receive. (*This should only be enabled in a test environment)
    
### Network map service
  Network map service can be enabled by providing the following config:
  ```
  networkMapConfig {
    cacheTimeout = 600000
    signInterval = 10000
  }
  ```
  `cacheTimeout`(ms) indicates how often the network map should poll the database for a newly signed network map. This is also added to the HTTP response header to set the node's network map update frequency.  
  `signInterval`(ms) this is only relevant when local signer is enabled. The signer poll the database according to the `signInterval`, and create a new network map if the collection of node info hashes is different from the current network map. 
    
##Example config file
```
basedir = "."
host = localhost
port = 0

#For local signing
rootStorePath = ${basedir}"/certificates/rootstore.jks"
keystorePath = ${basedir}"/certificates/caKeystore.jks"
keystorePassword = "password"
caPrivateKeyPassword = "password"

# Database config
dataSourceProperties {
  dataSourceClassName = org.h2.jdbcx.JdbcDataSource
  "dataSource.url" = "jdbc:h2:file:"${basedir}"/persistence;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000;WRITE_DELAY=0;AUTO_SERVER_PORT="${h2port}
  "dataSource.user" = sa
  "dataSource.password" = ""
}
h2port = 0

# Doorman config
# Comment out this section if running without doorman service
doormanConfig {
  approveInterval = 10000
  approveAll = false
  jiraConfig {
    address = "https://doorman-jira-host.com/"
    projectCode = "TD"
    username = "username"
    password = "password"
    doneTransitionCode = 41
  }
}

# Network map config
# Comment out this section if running without network map service
networkMapConfig {
  cacheTimeout = 600000
  signInterval = 10000
}
```

# Running the network

### 1. Create keystore for local signer

   If local signer is enabled, the server will look for keystores in the certificate folder on start up.  
   The keystores can be created using `--mode` flag.
   ```
   java -jar doorman-<version>.jar --mode ROOT_KEYGEN
   ```
   and 
   ```
   java -jar doorman-<version>.jar --mode CA_KEYGEN
   ```
   
   A trust store file containing the root trust certificate will be produced in the location `distribute-nodes / truststore.jks`
   (relative to `rootStorePath`). `truststore.jks` must be copied to the `certificates` directory of each node before 
   they attempt to register. The trust store password is `trustpass`.

### 2. Start Doorman service for notary registration 
   Start the network management server with the doorman service for initial bootstrapping. Network map service should be disabled at this point.  
   Comment out network map config in the config file and start the server by running :
   ```
   java -jar doorman-<version>.jar
   ```
   
### 3. Create notary node and register with the doorman
   After the doorman service is started, start the notary node for the `initial-registration` process.

### 4. Add notary identities to the network parameters
   The network parameters should contain the name and public key of the newly created notaries.  
      Example network parameters file:
      
      notaries : [{
          name: "O=Notary A, L=Port Louis, C=MU, OU=Org Unit, CN=Service Name"
          key: "GfHq2tTVk9z4eXgyWmExBB3JfHpeuYrk9jUc4zaVVSXpnW8FdCUNDhw6GRGN"
          validating: true
      }, {
          name: "O=Notary B, L=Bali, C=ID, OU=Org Unit, CN=Service Name"
          key: "GfHq2tTVk9z4eXgyEshv6vtBDjp7n76QZH5hk6VXLhk3vRTAmKcP9F9tRfPj"
          validating: false
      }]
      minimumPlatformVersion = 1
      maxMessageSize = 100
      maxTransactionSize = 100
      
   Save the parameters to `network-parameters.conf`

### 5. Load initial network parameters file for network map service
A network parameters file is required to start the network map service for the first time. The initial network parameters file can be loaded using the `--update-network-parameter` flag.
We can now restart the network management server with both doorman and network map service.  
```
java -jar doorman-<version>.jar --update-network-parameter network-parameters.conf
```
