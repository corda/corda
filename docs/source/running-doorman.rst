Running a doorman service
=========================


See the Readme in under ``network-management`` for detailed building instructions.


Configuration file
------------------
At startup doorman reads a configuration file, passed with ``--configFile`` on the command line.

This is an example of what a doorman configuration file might look like:
    .. literalinclude:: ../../network-management/doorman.conf

Invoke doorman with ``-?`` for a full list of supported command-line arguments.


Configuration parameters
------------------------
Allowed parameters are:

:keystorePassword: the keystore password

:caPrivateKeyPassword: the ca private key password

:rootKeystorePassword: Password for the root store

:rootPrivateKeyPassword: Password for the root private key

:address: The host and port on which doorman runs

:database: database properties. The same (including its default value) as for node configuration (see :doc:`corda-configuration-file`).

:dataSourceProperties: datasource properties

:doorman: Doorman specific configuration

    :approveAll: Whether to approve all requests (defaults to false), this is for debug only.

    :approveInterval: How often to process Jira approved requests in seconds.

    :jira: The Jira configuration for certificate signing requests

        :address: The URL to use to connect to Jira

        :projectCode: The project code on Jira

        :username: Username for Jira

        :password: Password for Jira

:revocation: Revocation service specific configuration

        :localSigning: Configuration for local CRL signing using the file key store. If not defined t

            :crlUpdateInterval: Validity time of the issued certificate revocation lists (in milliseconds).

            :crlEndpoint: REST endpoint under which the certificate revocation list can be obtained.
                          It is needed as this URL is encoded in the certificate revocation list itself.

        :crlCacheTimeout: Certificate revocation list cache entry expiry time (in milliseconds).
                          This value indicates for how long the crl is kept on the server side before querying the DB.

        :approveInterval: How often to process Jira approved requests in seconds.
                          Processing in this context means: querying the JIRA for approved/rejected request and syncing with the Doorman persistence.

        :approveAll: Whether to approve all requests (defaults to false), this is for debug only.

        :jira: The Jira configuration for certificate revocation requests

            :address: The URL to use to connect to Jira

            :projectCode: The project code on Jira

            :username: Username for Jira

            :password: Password for Jira

:networkMap: Network map specific configuration.

    :cacheTimeout: Network map cache entry expiry time (in milliseconds).

    :signInterval: How often to sign the network map in seconds.

:keystorePath: Path for the keystore. If not set (or null is passed) doorman will NOT perform any signing.
    This is required in case of the HSM integration where signing process is offloaded (from doorman) to an external service
    that binds with an HSM.

:rootStorePath: Path for the root keystore

Bootstrapping the network parameters
------------------------------------
When doorman is running it will serve the current network parameters. The first time doorman is
started it will need to know the initial value for the network parameters.

The initial values for the network parameters can be specified with a file, like this:
    .. literalinclude:: ../../network-management/network-parameters.conf

And the location of that file can be specified with: ``--update-network-parameters``.
Note that when reading from file:

1. ``epoch`` will always be set to 1,
2. ``modifiedTime`` will be the doorman startup time

``epoch`` will increase by one every time the network parameters are updated.

Bootstrapping the network map
-----------------------------
The network map is periodically refreshed, with frequency driven by the 'signInterval' parameter when local signing is in use.
In case of an external signing service it depends on that service configuration. Due to the design decisions dictated by the security concerns
around the external signing service, doorman is not allowed to connect directly with the signing sevice. Instead, the external service is
expected to access the doorman database in order to obtain signature requiring data.
Therefore, doorman takes a passive role considering all signing process related aspects.
Network map refresh happens only if there is a change to the current one (i.e. most recently created version of the network map).
See the :doc:`signing-service` for a more detailed description of the service.

When dealing with a fresh deployment (i.e. no previous data is present in the doorman database),
it may take some time until the network map is available. This is caused by the aforementioned decoupling of the signing
process from doorman itself.

Bootstrapping the certificate revocation list
---------------------------------------------
Upon doorman startup, the revocation service becomes available serving the certificate revocation list and providing endpoints
for certificate revocation request submission. It is assumed, that an empty signed CRL exists prior to the revocation service startup.
The revocation service exposes its API in two ways: via REST endpoints and via sockets.
While the former are meant to be used externally by network nodes (e.g. for the certificate revocation request submission,
certificate revocation list retrieval...), the latter is designed for internal communication with other proprietary services (e.g. HSM signing service).
The certificate revocation requests have the same lifecycle as the certificate signing requests.
For that purpose (and in the same manner) the revocation service is integrated with JIRA which is configured according
to the parameters specified in the doorman configuration file.
As mentioned, the revocation service provides the certificate revocation list. The list itself is signed externally (i.e. HSM signing service).
Therefore some delay, during the initial deployment of the service, is expected as those two services execute independently.

