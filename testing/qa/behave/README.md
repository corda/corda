The Behave Cucumber Scenarios defined under these sub-directories exercise R3 Corda and OS Corda distributions to include:
- Open Source master
- R3 Corda master
- Released versions of Open Source from V3.0 onwards
- Released versions of R3 Corda from R3 V3.0 DEV PREVIEW 3 onwards

**Compatibility** scenarios exercise:
- mixed networks: combinations of OS and R3 Corda nodes configured within an R3 Network
- mixed-versioned nodes: combinations of different versions of OS (master, V3.0, V3.1, etc) and R3 Corda (master, V3.0 DP, V3.0 GA) nodes within an R3 Network.
- mixed-versioned corDapps: combinations of nodes running different versions of CorDapps (both valid and invalid upgrade mixes).
- mixed-services: ability to continue transacting upon notaries, oracles, doorman upgrades & changes.

**Functional** scenarios exercise the key behaviours of R3 Corda components and node configurations to include:
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