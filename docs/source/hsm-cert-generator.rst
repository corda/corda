HSM Certificate Generation Tool
===============================

The purpose of the HSM Certificate Generation Tool is to provide means for corda certificate hierarchy generation on an HSM infrastructure.
The tool is intended to be executed automatically, therefore no human interaction is required. All necessary data is fed
to the tool from the provided configuration file.
In the configuration file, the tool expects 2 main parts to be configured:


* HSM device location

* HSM user(s) credentials, that will be used to gain access to the HSM device and to its functionality related to key/certificate generation.
  As such, it is assumed that the relevant privileged users are already set-up on the HSM prior to the script execution.
  The tool supports multi-user authentication, so it works with more strict policies, where there is a need for distributed privileges.

* Certificate configuration.
  The tool is designed to generate a single certificate in one execution. The type of the generated certificate is driven by the certificateType parameter.
  Currently in Corda there is a 2-level certificate hierarchy composed of a root certificate and 2 intermediate certificates (one for CSR and one for network map/parameters signing).
  Most attributes for those certificates are taken from the configuration file provided to the tool during the execution time. Some of them (e.g. aliases), however,
  are fixed in the code due to compatibility aspects.

In addition to the certificate hierarchy creation on the HSM side, the tool stores the created root CA certificate in a trust store, which is intended for distribution across the nodes.

IMPORTANT NOTE:
===============

Caution is required when the tool is used, especially in case where certificate attributes needs to be altered.
Incorrect usage of the tool may result in existing certificates being overriden and possible network corruption.
