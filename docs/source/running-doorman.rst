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

:host: host on which doorman runs

:port: port on which doorman runs

:mode: must be one of: DOORMAN (default), CA_KEYGEN, ROOT_KEYGEN.

:approveAll: Whether to approve all request (defaults to false), this is for debug only.

:databaseProperties: database properties

:dataSourceProperties: datasoruce properties

:jiraConfig: The Jira configuration

    :address: The URL to use to connect to Jira

    :projectCode: The project code on Jira

    :username: Username for Jira

    :password: Password for Jira

    :doneTransitionCode: Jira status to put approved tickets in

:keystorePath: Path for the keystore. If not set (or null is passed) doorman will NOT perform any signing.
    This is required in case of the HSM integration where signing process is offloaded (from doorman) to an external service
    that binds with an HSM.

:rootStorePath: Path for the root keystore

:approveInterval: How often to process Jira approved requests in seconds

:signInterval: How often to sign the network map in seconds

Bootstrapping the network parameters
------------------------------------
When doorman is running it will serve the current network parameters. The first time doorman is
started it will need to know the initial value for the network parameters.

The initial values for the network parameters can be specified with a file, like this:
    .. literalinclude:: ../../network-management/initial-network-parameters.conf

And the location of that file can be specified with: ``--initialNetworkParameters``.
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