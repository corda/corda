Running the signing service
===========================

The signing service is a bridge between the networking service and the HSM infrastructure. It is responsible for retrieving
pending requests for signatures and managing the process of securing these signatures from an HSM infrastructure.

The signing service has a console-based user interface (designed for the manual signing process of the certificate signing requests),
which upon successful startup should display different options to the user.
At the same time, it connects to the database (which is expected to be shared with Doorman)
and periodically polls it and if needed automatically signs the following: network map and certificate revocation list.

See the :doc:`signing-service` for a more detailed description of the service.

Configuration file
------------------
At startup the signing service reads a configuration file, passed with ``--config-file`` on the command line.

This is an example of what a signing service configuration file might look like:
    .. literalinclude:: ../../network-management/hsm.conf

Invoke the signing service with ``-?`` for a full list of supported command-line arguments.


Configuration parameters
------------------------
Allowed parameters are:

:device: HSM connection string. It is of the following format 3001@127.0.0.1, where 3001 is the port number.
    Default value: "3001@127.0.0.1"

:rootKeyStoreFile: Location of the key store (trust store) containing the root certificate.

:rootKeyStorePassword: Password for the key store (trust store) containing the root certificate.

:networkMapKeyGroup: HSM key group for the network map certificate key. This parameter is vendor specific (see Utimaco docs).

:doormanKeyGroup: HSM key group for the doorman certificate key. This parameter is vendor specific (see Utimaco docs).

:keySpecifier: HSM key specifier. This parameter is vendor specific (see Utimaco docs). Default value: 1.

:rootPrivateKeyPassword: Private key password for the root certificate.

:csrPrivateKeyPassword: Private key password for the intermediate certificate used to sign certficate signing requests.

:csrCertCrlDistPoint: Certificate revocation list location for the node CA certificate.

:csrCertCrlIssuer: Certificate revocation list issuer. The expected value is of the X500 name format - e.g. "L=London, C=GB, OU=Org Unit, CN=Service Name".
                   If not specified, the node CA certificate issuer is considered also as the CRL issuer.

:databaseProperties: Database properties.

:dataSourceProperties: Data source properties. It should describe (or point to) the Doorman database.

:networkMapPrivateKeyPassword: Private key password for the intermediate certificate used to sign the network map.

:validDays: Number of days issued signatures are valid for.

:signAuthThreshold: Minimum authentication strength threshold required for certificate signing requests.
    Default value: 2

:keyGenAuthThreshold: Minimum authentication strength threshold required for key generation.
    Default value: 2

:authMode: Authentication mode, used when validating a user for certificate signing request signature.
    Allowed values are:
        "PASSWORD" (default) - type-in password authentication
        CARD_READER - smart card reader authentication
        KEY_FILE - key file authentication

:authKeyFilePath: Authentication key file. It is used when the 'authMode' is set to "KEY_FILE"
    or for the automated signing process - e.g. network map, certificate revocation list. Default value: null

:authKeyFilePassword: Authentication key file password. It is used when the 'authMode' is set to "KEY_FILE"
        or for the automated signing process - e.g. network map, certificate revocation list. Default value: null

:signInterval: Interval (in milliseconds) in which all automated signing happens. Default value: 60000 milliseconds

Expected behaviour and output upon the service start-up
-------------------------------------------------------

A commandline-based interface (with different menu options) is presented to a user.