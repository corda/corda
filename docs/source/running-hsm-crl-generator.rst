Running the HSM Certificate Generation tool
===========================================

The purpose of this tool is to facilitate the process of CRL generation using the ROOT certificate stored on the HSM infrastructure.
See :doc:`hsm-crl-generator` for more details.


See the Readme under ``network-management`` for detailed building instructions.


Configuration file
------------------
At startup, the HSM CRL Generation Tool reads a configuration file, passed with ``--config-file`` on the command line.

This is an example of what a tool configuration file might look like:
    .. literalinclude:: ../../network-management/crl-generator.conf

General configuration parameters
--------------------------------
Allowed parameters are:

:hsmHost: IP address of the HSM device.

:hsmPort: Port number of the HSM device.

:userConfigs: List of user authentication configurations. See below section on User Authentication Configuration.

:crl: CRL specific configuration. See below section on CRL Configuration.

:trustStoreFile: Path to the trust store file containing the ROOT certificate.

:trustStorePassword: Password for the trust store.


CRL Configuration
-----------------

:keySpecifier: This is an HSM specific parameter that corresponds to ROOT key name spacing. See Utimaco documentation for more details.

:keyGroup: This is an HSM specific parameter that corresponds to ROOT key name grouping. See Utimaco documentation for more details.

:validDays: Validity period of this CRL expressed in days.

:crlEndpoint: URL pointing to the endpoint where this CRL can be obtained from. It is embedded in the generated CRL.

:indirectIssuer: A boolean flag noting whether this CRL was issued by the certificate issuer (false) or another issuer (true).

:filePath: Path to the generated file.

:revocations: A list of revoked certificate data that is to be included in the generated CRL. Default value is the empty list.
              See below for more details on the revoked certificate data.

Revoked Certificate Data
------------------------

:certificateSerialNumber: Serial number of the revoked certificate.

:dateInMillis: Certificate revocation time.

:reason: Reason for the certificate revocation. The allowed value is one of the following:
         UNSPECIFIED, KEY_COMPROMISE, CA_COMPROMISE, AFFILIATION_CHANGED, SUPERSEDED, CESSATION_OF_OPERATION, PRIVILEGE_WITHDRAWN

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