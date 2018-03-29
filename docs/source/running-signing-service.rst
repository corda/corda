Running the signing service
===========================

The signing service is a bridge between the networking service and the HSM infrastructure. It is responsible for retrieving
pending requests for signatures and managing the process of securing these signatures from an HSM infrastructure.

The signing service has two execution modes. Each mode focuses on signing one of the two different types of data: certificate signing requests and network map.
Signing of the network map is an automatic process (i.e. does not require human intervention) that retrieves from the database the network map data to be signed.
Certificate signing requests, on the other hand, require human-in-the-loop to be processed and therefore the signing process relies on the console-based interface, that allows for user interaction.
Depending on the configuration each of those processes can be enabled or disabled (see below for more details).

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

:keySpecifier: HSM key specifier. This parameter is vendor specific (see Utimaco docs).

:database: Database properties.

:dataSourceProperties: Data source properties. It should describe (or point to) the Doorman database.

:doorman: CSR signing process configuration parameters. If specified, the signing service will sign approved CSRs.

    :validDays: Number of days issued signatures are valid for.

    :rootKeyStoreFile: Location of the key store (trust store) containing the root certificate.

    :rootKeyStorePassword: Password for the key store (trust store) containing the root certificate.

    :keyGroup: HSM key group for the doorman certificate key. This parameter is vendor specific (see Utimaco docs).

    :crlDistributionPoint: Certificate revocation list location for the node CA certificate.

    :crlServerSocketAddress: Address of the socket connection serving the certificate revocation list.

    :crlUpdatePeriod: Validity time of the issued certificate revocation lists (in milliseconds).

    :authParameters: Authentication configuration for the CSR signing process.

        :mode: Authentication mode. Allowed values are: PASSWORD, CARD_READER and KEY_FILE

        :password: Key file password. Valid only if the authentication mode is set to KEY_FILE.

        :keyFilePath: Key file path. Valid only if authentication mode is set to KEY_FILE.

        :threshold: Minimum authentication strength threshold required for certificate signing requests.

:networkMap: Network map signing process configuration parameters. If specified, the signing service will sign the network map.

    :username: HSM username to be used when communicating with the HSM.

    :keyGroup: HSM key group for the network map certificate key. This parameter is vendor specific (see Utimaco docs).

    :authParameters: Authentication configuration for the CSR signing process.

            :mode: Authentication mode. Allowed values are: PASSWORD and KEY_FILE

            :password: If the authentication mode is set to KEY_FILE, then it is the key file password.
                       If the authentication mode is set to PASSWORD, then it is the password string.

            :keyFilePath: Key file path. Valid only if authentication mode is set to KEY_FILE.

            :threshold: Minimum authentication strength threshold required for certificate signing requests.


Expected behaviour and output upon the service start-up
-------------------------------------------------------

A commandline-based interface (with different menu options) is presented to a user.