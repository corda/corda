Running a doorman service
=========================


See the Readme in under ``network-management`` for detailed building instructions.


Configuration file
------------------
At startup Doorman reads a configuration file, passed with ``--configFile`` on the command line.

This is an example of what a Doorman configuration file might look like:
    .. literalinclude:: ../../network-management/doorman.conf

Invoke Doorman with ``-?`` for a full list of supported command-line arguments.


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
