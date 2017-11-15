Running a doorman service
=========================


See the Readme in under ``network-management`` for detailed building instructions.


Configuration file
------------------
At startup Doorman reads a configuration file, passed with ``--configFile`` on the command line.

This is an example of what a Doorman configuration file might look like:
    .. literalinclude:: ../../network-management/doorman.conf

Invoke Doorman with ``-?`` for a full list of supported command-line arguments.


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

:keystorePath: Path for the keystore

:rootStorePath: Path for the root keystore

:approveInterval: How often to process Jira approved requests in seconds

:signInterval: How often to sign the network map in seconds

Bootstrapping the network parameters
------------------------------------
When Doorman is running it will serve the current network parameters. The first time Doorman is
started it will need to know the initial value for the network parameters.

The initial values for the network parameters can be specified with a file, like this:
    .. literalinclude:: ../../network-management/initial-network-parameters.conf

And the location of that file can be specified with: ``--initialNetworkParameters``.
Note that when reading from file:

1. ``epoch`` will always be set to 1,
2. ``modifiedTime`` will be the Doorman startup time

``epoch`` will increase by one every time the network parameters are updated.
