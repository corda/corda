Running the HSM Certificate Generation tool
===========================================

The purpose of this tool is to facilitate the process of certificate generation on the HSM infrastructure.
See :doc:`hsm-cert-generator` for more details.


See the Readme under ``network-management`` for detailed building instructions.


Configuration file
------------------
At startup, the HSM Certificate Generation Tool reads a configuration file, passed with ``--config-file`` on the command line.

This is an example of what a tool configuration file might look like:
    .. literalinclude:: ../../network-management/cert-generator.conf

General configuration parameters
--------------------------------
Allowed parameters are:

:hsmHost: IP address of the HSM device.

:hsmPort: Port number of the HSM device.

:userConfigs: List of user authentication configurations. See below section on User Authentication Configuration.

:certConfig: Certificate specific configuration. See below section on Certificate Configuration.

:trustStoreDirectory: Path to the directory where the generated trust store should be placed.
                 The name of the generated file is "network-root-truststore.jks".
                 If the trust store file does not exist, it will be created.
                 IMPORTANT - This trust store is intended to be distributed across the nodes.
                 Nodes are hardcoded to use "network-root-truststore.jks" file as the trust store name.
                 As such, it is required that the file name is as the one expected by nodes.

:trustStorePassword: Password for the generated trust store.


Certificate Configuration
-------------------------

:certificateType: Type of the certificate to be created. Allowed values are:
                  ROOT_CA, INTERMEDIATE_CA, NETWORK_MAP.

:rootKeyGroup: This is an HSM specific parameter that corresponds to key name spacing for the root key. It is ignored if the certificateType value is ROOT_CA. See Utimaco documentation for more details.

:subject: X500Name formatted string to be used as the certificate public key subject.

:validDays: Days number for certificate validity.

:crlDistributionUrl: Url to the certificate revocation list of this certificate. If not defined the CRL information will not be added to the certificate.

:crlIssuer: X500 name of the certificate revocation list issuer - e.g. "L=London, C=GB, OU=Org Unit, CN=Service Name". If the crlDistributionUrl configuration option is specified but this parameter is not, then the certificate issuing authority is considered to be the CRL issuer for this certificate.

:keyCurve: Key algorithm curve type. See Utimaco supported values. "NIST-P256" has been used for experiments.

:keyExport: Enables key exporting. 1 for allow, 0 for deny.

:keyGenMechanism: HSM key generation process specific options. In the experiments the integer value being the logic OR of the two following (MECH_KEYGEN_UNCOMP = 4 or MECH_RND_REAL = 0) has been used. See Utimaco documentation for more details.

:keyOverride: Whether to override the key if already exists or not. 1 for override and 0 for NOT override.

:keySpecifier: This is an HSM specific parameter that corresponds to key name spacing of the generated key. See Utimaco documentation for more details.

:keyGroup: This is an HSM specific parameter that corresponds to key name grouping of the generated key. See Utimaco documentation for more details.


User Authentication Configuration
---------------------------------
Allowed parameters are:

:username: HSM username. This user needs to be allowed to generate keys/certificates and store them in HSM.

:authMode: One of the 3 possible authentication modes:
           PASSWORD - User's password as set-up in the HSM
           CARD_READER - Smart card reader authentication
           KEY_FILE - Key file based authentication.

:authToken: Depending on the authMode it is either user's password or path to the authentication key file. In case of the CARD_READER authMode value, this can be omitted.

:keyFilePassword: Only relevant, if authMode == KEY_FILE. It is the key file password.