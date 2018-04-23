Running the dev keystore generator
==================================

The dev keystore generator is a utility tool designed only for internal use. Sometimes our certificates change (e.g. new
extensions are added, some of them are modified...). In order to stay consistent with the rest of the Corda platform and in
particular with Corda node (and its DEV execution mode), we need a facility that would allow us to easily create keystore containing
both root and doorman certificates together with their keys. Those certificates will reflect the most recent state of the Corda certificates.
In addition, a truststore file (containing the root certificate) is also generated. Once generated, those files (i.e. keystore and truststore)
can be copied to an appropriate node directory.

Although, the output of the tool is strongly bound to the node execution process (i.e. expected key store file name, trust store file name, passwords are hardcoded),
it can be used to generate arbitrary keystore and truststore files with Corda certificates. Therefore, the tool supports a custom configuration.

Configuration file
------------------
At startup the dev generator tool reads a configuration file, passed with ``--config-file`` on the command line.

This is an example of what a generator configuration file might look like:
    .. literalinclude:: ../../network-management/dev-generator.conf

Invoke the tool with ``-?`` for a full list of supported command-line arguments.

If no configuration file is provided, all the options default to the node expected values.


Configuration parameters
------------------------
Allowed parameters are:

:privateKeyPass: Password for both Root and Doorman private keys. Default value: "cordacadevkeypass".

:keyStorePass: Password for the keystore file. Default value: "cordacadevpass".

:keyStoreFileName: File name for the keystore file. Default value: "cordadevcakeys.jks".

:trustStorePass: Password for the truststore file. Default value: "trustpass".

:trustStoreFileName: File name for the truststore file. Default value: "cordatruststore.jks".

:directory: Directory in which both keystore and trustore files should be created. Default value: "./certificates"