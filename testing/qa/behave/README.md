Overview
========

The Behave Cucumber Scenarios defined under these sub-directories exercise Corda Enterprise and OS Corda distributions to include:
- Open Source master
- Corda Enterprise master
- Released versions of Open Source from V3.0 onwards
- Released versions of Corda Enterprise from V3.0 DEV PREVIEW 3 onwards

**Compatibility** scenarios exercise:
- mixed networks: combinations of OS and Corda Enterprise nodes configured within an R3 Network
- mixed-versioned nodes: combinations of different versions of OS (master, V3.0, V3.1, etc) and Corda Enterprise (master, V3.0 DP, V3.0 GA) nodes within an R3 Network.
- mixed-versioned corDapps: combinations of nodes running different versions of CorDapps (both valid and invalid upgrade mixes).
- mixed-services: ability to continue transacting upon notaries, oracles, doorman upgrades & changes.

**Functional** scenarios exercise the key behaviours of Corda Enterprise components and node configurations to include:
- basic cash management functions (issuance, transfer, redemption)
- vault usage of different database providers (H2, sql-server, postgreSQL)
- doorman certificate issuance and usage
- compatibility zone network parameter registration and updates
- network map registration and updates
- notary registration and updates

NOTE:
- the CTS framework uses R3's internal artifactory repositories to setup/configure officially released versions of Corda:
    1. https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases
    2. https://ci-artifactory.corda.r3cev.com/artifactory/r3-corda-releases
- Scenarios specifying `master` will use the latest code checked into the respective Open Source and Enterprise repositories:
    1. https://github.com/corda/corda
    2. https://github.com/corda/enterprise/
    
Further goals of this project are to:
- automatically generate and register Docker images with target Azure Test Environments (eg. Functional/QA, CTS)
- effect execution of scenarios in specified Target Environments 
- use Before and After hooks to pre-configure Target Environments to allow batch running of multiple scenarios (eg. a given
  feature will specify the environment once for one or many scenarios)
  
QA setup and usage instructions
===============================

Setup
-----
Set up the staging area which will hold all the distributions of Corda referenced in the test scenarios:

- specify the staging root directory: 

```bash
  $export STAGING_ROOT=$HOME/staging
```

- if any of your tests are referencing a master version of [Corda OS](https://github.com/corda/corda), checkout the latest version of the Open Source repo and run:

```bash
  $ cd experimental/behave
  $ ./prepare.sh 
```

   You should now see the following artifacts in your staging area:
   
```bash
$ ls -lR /Users/home/staging/corda/corda-master
total 285440
drwxr-xr-x  3 user  staff        96  4 Jun 16:11 apps
-rw-r--r--  1 user  staff  55439705  4 Jun 16:11 corda.jar
-rw-r--r--  1 user  staff  90699598  4 Jun 16:11 network-bootstrapper.jar

/Users/home/staging/corda/corda-master/apps:
total 4688
-rw-------  1 user  staff  2396954  4 Jun 16:11 corda-finance-4.0-SNAPSHOT.jar   
```

- similarly, if any of your tests are referencing a master version of [Corda Enterprise](https://github.com/corda/enterprise), checkout the latest version of the Enterprise repo and run:

```bash
  $ cd experimental/behave
  $ ./prepare.sh 
```
   
   You should now see the following artifacts in your staging area:

```bash
$ ls -lR /Users/home/staging/corda/r3corda-master/
total 503264
drwxr-xr-x  6 user  staff        192  4 Jun 16:39 apps
-rw-r--r--  1 user  staff    6239281  4 Jun 16:39 corda-rpcProxy.jar
-rw-r--r--  1 user  staff   66441262  4 Jun 16:38 corda.jar
-rw-r--r--  1 user  staff   71987460  4 Jun 16:39 dbmigration.jar
-rw-r--r--  1 user  staff  112985324  4 Jun 16:39 network-bootstrapper.jar
-rwxr-xr-x  1 user  staff       1224  4 Jun 16:39 startRPCproxy.sh

/Users/home/staging/corda/r3corda-master/apps:
total 82248
-rw-------  1 user  staff   2418144  4 Jun 16:38 corda-finance-R3.CORDA-3.0-SNAPSHOT.jar
```

Notes: 

- all versions of Corda published to the Artifactory release repositories are automatically downloaded and configured
on the fly as part of a test scenario.      
- Docker must be running for SQL Server, Oracle and PostrgeSQL tests.

Usage
-----

Checkout the latest version of the Enterprise repo and run:

```bash
  $cd experimental/behave
  ../../gradlew behaveJar
````

Change to the QA testing directory containing the scenario scripts and run:
```bash
  $cd ../../test/qa
  $export BEHAVE_JAR=$(ls ../../../experimental/behave/build/libs/corda-behave-*.jar | tail -n1)
```

To verify the tool is ready to be used run:

```bash
$ java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR}
```

You should now see the following output providing details on how to run the tool:

```bash
Missing required option(s) [path]
Usage: ScenarioRunner [options] --path <location of feature scenario definitions>

Examples:
    ScenarioRunner -path <features-dir>
    ScenarioRunner -path <features-dir>/<name>.feature
    ScenarioRunner -path <features-dir>/<name>.feature:3:9

    ScenarioRunner -path <features-dir> --plugin html --tags @qa
    ScenarioRunner -path <features-dir> --plugin html --tags @compatibility

Please refer to the Cucumber documentation https://cucumber.io/docs/reference/jvm for more info.

Option (* = required)                  Description                          
---------------------                  -----------                          
-d                                                                          
--glue [location of additional step    (default: net.corda.behave.scenarios)
  definitions, hooks and plugins]                                           
* --path <Path location of .feature                                         
  specifications>                                                           
--plugin [register additional plugins  (default: pretty)                    
  (see https://cucumber.                                                    
  io/docs/reference/jvm)]                                                   
--tags [only run scenarios marked as                                        
  @<tag-name>]  
```
Note: passing in a *-d* option will perform a dry run only (validates the syntax of the scenario but does not execute the code)

Scenario scripts
----------------  
There are currently two sets of scripts:
1. Functional

```bash
# the Cucumber behave test scenario definitions themselves
$ls -l functional/resources/features/functional.feature
# Unix script to easily run these
$ls -l functional/resources/scripts/run-functional.sh
```

2. Interoperability & Compatibility

```bash
# the Cucumber behave test scenario definitions themselves
$ls -l compatibility/resources/features/interoperability.feature
# Unix script to easily run these
$ls -l compatibility/resources/scripts/run-interoperability.sh
```

Note: the complete suite of Compatibility test scenarios have note yet been fully implemented.

You can now run the above test scripts in a number of different ways:

* as a complete suite called a *feature set*:

```bash
# run all scenarios in the functional.feature file
$java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR} -path functional/resources/features/functional.feature
```

```bash
# run all scenarios in the interoperability.feature file
$java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR} -path compatibility/resources/features/interoperability.feature
```

* as a set of tagged scenarios within a feature file: 

```bash
# run all scenarios tagged with qa in the interoperability.feature file
$java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR} -path compatibility/resources/features/interoperability.feature:@qa
```

* as an individual scenario for all example configurations:

```bash
# run the scenario defined on line 5 of the interoperability.feature file
$java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR} -path compatibility/resources/features/interoperability.feature:5

# by default the above will run as many times as there are parameterised variable definitions

    Examples:
      | R3-Corda-Node-Version        | Corda-Node-Version | Currency |
      | r3-master                    | corda-master       | GBP      |
      | corda-3.0                    | corda-3.1          | GBP      |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.0          | GBP      |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.1          | GBP      |
```

* as an individual scenario for a single example configuration you will need to comment out the other example line items:

```bash
# run the scenario defined on line 5 of the interoperability.feature file
$java -DSTAGING_ROOT=${STAGING_ROOT} -jar ${BEHAVE_JAR} -path compatibility/resources/features/interoperability.feature:5

# by default the above will the scenario using *corda-3.0* and *corda-3.1* node versions only:

    Examples:
      | R3-Corda-Node-Version        | Corda-Node-Version | Currency |
#      | r3-master                    | corda-master       | GBP      |
      | corda-3.0                    | corda-3.1          | GBP      |
#      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.0          | GBP      |
#      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.1          | GBP      |
```
