Node configuration
==================

.. contents::

Configuration file location
---------------------------
When starting a node, the ``corda.jar`` file defaults to reading the node's configuration from a ``node.conf`` file in the directory from which the command to launch Corda is executed.
There are two command-line options to override this behaviour:

* The ``--config-file`` command line option allows you to specify a configuration file with a different name, or in a different file location.
  Paths are relative to the current working directory

* The ``--base-directory`` command line option allows you to specify the node's workspace location.
  A ``node.conf`` configuration file is then expected in the root of this workspace.

If you specify both command line arguments at the same time, the node will fail to start.

Configuration file format
-------------------------
The Corda configuration file uses the HOCON format which is a superset of JSON. Please visit
`<https://github.com/typesafehub/config/blob/master/HOCON.md>`_ for further details.

Please do NOT use double quotes (``"``) in configuration keys.

Node setup will log ``Config files should not contain " in property names. Please fix: [key]`` as an error when it finds double quotes around keys.
This prevents configuration errors when mixing keys containing ``.`` wrapped with double quotes and without them e.g.: The property
``"dataSourceProperties.dataSourceClassName" = "val"`` in `Reference.conf`_ would be not overwritten by the property
``dataSourceProperties.dataSourceClassName = "val2"`` in *node.conf*.

By default the node will fail to start in presence of unknown property keys.
To alter this behaviour, the ``on-unknown-config-keys`` command-line argument can be set to ``IGNORE`` (default is ``FAIL``).

Overriding values from node.conf
--------------------------------

Environment variables
  For example: ``${NODE_TRUST_STORE_PASSWORD}`` would be replaced by the contents of environment variable ``NODE_TRUST_STORE_PASSWORD`` (see: :ref:`hiding-sensitive-data` section).

JVM options
  JVM options or environmental variables prefixed with ``corda.`` can override ``node.conf`` fields.
  Provided system properties can also set values for absent fields in ``node.conf``.
  This is an example of adding/overriding the keyStore password :

  .. sourcecode:: shell

    java -Dcorda.rpcSettings.ssl.keyStorePassword=mypassword -jar node.jar

Configuration file fields
-------------------------

.. note :: The available configuration fields are listed below in alphabetic order.

.. _corda-configuration-file-fields:

additionalP2PAddresses
  An array of additional host:port values, which will be included in the advertised NodeInfo in the network map in addition to the :ref:`p2pAddress <corda_configuration_file_p2pAddress>`.
  Nodes can use this configuration option to advertise HA endpoints and aliases to external parties.

  *Default:* empty list

attachmentContentCacheSizeMegaBytes
  Optionally specify how much memory should be used to cache attachment contents in memory.

  *Default:* 10MB

attachmentCacheBound
  Optionally specify how many attachments should be cached locally. Note that this includes only the key and metadata, the content is cached separately and can be loaded lazily.

  *Default:* 1024


compatibilityZoneURL (deprecated)
  The root address of the Corda compatibility zone network management services, it is used by the Corda node to register with the network and obtain a Corda node certificate, (See :doc:`permissioning` for more information.) and also is used by the node to obtain network map information.
  Cannot be set at the same time as the :ref:`networkServices <corda_configuration_file_networkServices>` option.

  **Important:  old configuration value, please use networkServices**

  *Default:* not defined

.. _corda_configuration_file_signer_blacklist:

cordappSignerKeyFingerprintBlacklist
  List of the public keys fingerprints (SHA-256 of public key hash) not allowed as Cordapp JARs signers.
  The node will not load Cordapps signed by those keys.
  The option takes effect only in production mode and defaults to Corda development keys (``["56CA54E803CB87C8472EBD3FBC6A2F1876E814CEEBF74860BD46997F40729367", "83088052AF16700457AE2C978A7D8AC38DD6A7C713539D00B897CD03A5E5D31D"]``), in development mode any key is allowed to sign Cordpapp JARs.

  *Default:* not defined

crlCheckSoftFail
  This is a boolean flag that when enabled (i.e. ``true`` value is set) causes certificate revocation list (CRL) checking to use soft fail mode.
  Soft fail mode allows the revocation check to succeed if the revocation status cannot be determined because of a network error.
  If this parameter is set to ``false`` rigorous CRL checking takes place. This involves each certificate in the certificate path being checked for a CRL distribution point extension, and that this extension points to a URL serving a valid CRL.
  This means that if any CRL URL in the certificate path is inaccessible, the connection with the other party will fail and be marked as bad.
  Additionally, if any certificate in the hierarchy, including the self-generated node SSL certificate, is missing a valid CRL URL, then the certificate path will be marked as invalid.

  *Default:* true

custom
  Set custom command line attributes (e.g. Java system properties) on the node process via the capsule launcher

  jvmArgs:
      A list of JVM arguments to apply to the node process. This removes any defaults specified from ``corda.jar``, but can be overridden from the command line.
      See :ref:`setting_jvm_args` for examples and details on the precedence of the different approaches to settings arguments.

      *Default:* not defined

.. _database_properties_ref:

database
  Database configuration

  transactionIsolationLevel:
    Transaction isolation level as defined by the ``TRANSACTION_`` constants in ``java.sql.Connection``, but without the ``TRANSACTION_`` prefix.

    *Default:* ``REPEATABLE_READ``

  exportHibernateJMXStatistics:
    Whether to export Hibernate JMX statistics.

    **Caution: enabling this option causes expensive run-time overhead**

    *Default:* false

  initialiseSchema
    Boolean which indicates whether to update the database schema at startup (or create the schema when node starts for the first time).
    If set to ``false`` on startup, the node will validate if it's running against a compatible database schema.

    *Default:* true

  initialiseAppSchema
    The property allows to override ``database.initialiseSchema`` for the Hibernate DDL generation for CorDapp schemas.
    ``UPDATE`` performs an update of CorDapp schemas, while ``VALID`` only verifies their integrity and ``NONE`` performs no check.
    When ``initialiseSchema`` is set to ``false``, then ``initialiseAppSchema`` may be set as ``VALID`` or ``NONE`` only.

    *Default:* CorDapp schema creation is controlled with ``initialiseSchema``.

dataSourceProperties
  This section is used to configure the JDBC connection and database driver used for the node's persistence.
  :ref:`Node database <standalone_database_config_examples_ref>` contains example configurations for other database providers.
  To add additional data source properties (for a specific JDBC driver) use the ``dataSource.`` prefix with the property name (e.g. ``dataSource.customProperty = value``).

  dataSourceClassName
    JDBC Data Source class name.

  dataSource.url
    JDBC database URL.

  dataSource.user
    Database user.

  dataSource.password
    Database password.

  *Default:*

  .. code-block:: none

    dataSourceClassName = org.h2.jdbcx.JdbcDataSource
    dataSource.url = "jdbc:h2:file:"${baseDirectory}"/persistence;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0;LOCK_TIMEOUT=10000"
    dataSource.user = sa
    dataSource.password = ""

detectPublicIp
  This flag toggles the auto IP detection behaviour.
  If enabled, on startup the node will attempt to discover its externally visible IP address first by looking for any public addresses on its network interfaces, and then by sending an IP discovery request to the network map service.
  Set to ``true`` to enable.

  *Default:* false

.. _corda_configuration_file_dev_mode:

devMode
  This flag sets the node to run in development mode.
  On startup, if the keystore ``<workspace>/certificates/sslkeystore.jks``
  does not exist, a developer keystore will be used if ``devMode`` is true.
  The node will exit if ``devMode`` is false and the keystore does not exist.
  ``devMode`` also turns on background checking of flow checkpoints to shake out any bugs in the checkpointing process.
  Also, if ``devMode`` is true, Hibernate will try to automatically create the schema required by Corda or update an existing schema in the SQL database; if ``devMode`` is false, Hibernate will simply validate the existing schema, failing on node start if the schema is either not present or not compatible.
  If no value is specified in the node configuration file, the node will attempt to detect if it's running on a developer machine and set ``devMode=true`` in that case.
  This value can be overridden from the command line using the ``--dev-mode`` option.

  *Default:* Corda will try to establish based on OS environment

devModeOptions
  Allows modification of certain ``devMode`` features

  **Important: This is an unsupported configuration.**

  allowCompatibilityZone
    Allows a node configured to operate in development mode to connect to a compatibility zone.

    *Default:* not defined


emailAddress
  The email address responsible for node administration, used by the Compatibility Zone administrator.

  *Default:* company@example.com

extraNetworkMapKeys
  An optional list of private network map UUIDs. Your node will fetch the public network and private network maps based on these keys.
  Private network UUID should be provided by network operator and lets you see nodes not visible on public network.

  **Important: This is a temporary feature for onboarding network participants that limits their visibility for privacy reasons.**

  *Default:* not defined


flowMonitorPeriodMillis
  Duration of the period suspended flows waiting for IO are logged.

  *Default:* 60 seconds

flowMonitorSuspensionLoggingThresholdMillis
  Threshold duration suspended flows waiting for IO need to exceed before they are logged.

  *Default:* 60 seconds

flowTimeout
  When a flow implementing the ``TimedFlow`` interface and setting the ``isTimeoutEnabled`` flag does not complete within a defined elapsed time, it is restarted from the initial checkpoint.
  Currently only used for notarisation requests with clustered notaries: if a notary cluster member dies while processing a notarisation request, the client flow eventually times out and gets restarted.
  On restart the request is resent to a different notary cluster member in a round-robin fashion. Note that the flow will keep retrying forever.

  timeout
    The initial flow timeout period.

    *Default:* 30 seconds

  maxRestartCount
    The number of retries the back-off time keeps growing for.
    For subsequent retries, the timeout value will remain constant.

    *Default:* 6

  backoffBase
    The base of the exponential backoff, `t_{wait} = timeout * backoffBase^{retryCount}`

    *Default:* 1.8

h2Port (deprecated)
  Defines port for h2 DB.

  **Important: Deprecated please use h2Setting instead**

.. _corda_configuration_file_h2_settings:

h2Settings
  Sets the H2 JDBC server host and port.
  See :doc:`node-database-access-h2`.
  For non-localhost address the database password needs to be set in ``dataSourceProperties``.

  *Default:* NULL

jarDirs
  An optional list of file system directories containing JARs to include in the classpath when launching via ``corda.jar`` only.
  Each should be a string.
  Only the JARs in the directories are added, not the directories themselves.
  This is useful for including JDBC drivers and the like. e.g. ``jarDirs = [ ${baseDirectory}"/libs" ]``.
  (Note that you have to use the ``baseDirectory`` substitution value when pointing to a relative path).

  *Default:* not defined

jmxMonitoringHttpPort
  If set, will enable JMX metrics reporting via the Jolokia HTTP/JSON agent on the corresponding port.
  Default Jolokia access url is http://127.0.0.1:port/jolokia/

  *Default:* not defined

jmxReporterType
  Provides an option for registering an alternative JMX reporter.
  Available options are ``JOLOKIA`` and ``NEW_RELIC``.

  The Jolokia configuration is provided by default.
  The New Relic configuration leverages the Dropwizard_ NewRelicReporter solution.
  See `Introduction to New Relic for Java`_ for details on how to get started and how to install the New Relic Java agent.

  *Default:* ``JOLOKIA``

  .. _Dropwizard: https://metrics.dropwizard.io/3.2.3/manual/third-party.html
  .. _Introduction to New Relic for Java: https://docs.newrelic.com/docs/agents/java-agent/getting-started/introduction-new-relic-java

keyStorePassword
  The password to unlock the KeyStore file (``<workspace>/certificates/sslkeystore.jks``) containing the node certificate and private key.

  **Important: This is the non-secret value for the development certificates automatically generated during the first node run.
  Longer term these keys will be managed in secure hardware devices.**

  *Default:* cordacadevpass

lazyBridgeStart
  Internal option.

  **Important: Please do not change.**

  *Default:* true

messagingServerAddress
  The address of the ArtemisMQ broker instance.
  If not provided the node will run one locally.

  *Default:* not defined

messagingServerExternal
  If ``messagingServerAddress`` is specified the default assumption is that the artemis broker is running externally.
  Setting this to ``false`` overrides this behaviour and runs the artemis internally to the node, but bound to the address specified in ``messagingServerAddress``.
  This allows the address and port advertised in ``p2pAddress`` to differ from the local binding, especially if there is external remapping by firewalls, load balancers , or routing rules. Note that ``detectPublicIp`` should be set to ``false`` to ensure that no translation of the ``p2pAddress`` occurs before it is sent to the network map.

  *Default:* not defined

.. _corda_configuration_file_myLegalName:

myLegalName
  The legal identity of the node.
  This acts as a human-readable alias to the node's public key and can be used with the network map to look up the node's info.
  This is the name that is used in the node's certificates (either when requesting them from the doorman, or when auto-generating them in dev mode).
  At runtime, Corda checks whether this name matches the name in the node's certificates.
  For more details please read :ref:`node-naming` chapter.

  *Default:* not defined

.. _corda_configuration_file_notary:

notary
  Optional configuration object which if present configures the node to run as a notary. If part of a Raft or BFT-SMaRt
  cluster then specify ``raft`` or ``bftSMaRt`` respectively as described below. If a single node notary then omit both.

  validating
    Boolean to determine whether the notary is a validating or non-validating one.

    *Default:* false

  serviceLegalName
    If the node is part of a distributed cluster, specify the legal name of the cluster.
    At runtime, Corda checks whether this name matches the name of the certificate of the notary cluster.

    *Default:* not defined

  raft
    *(Experimental)* If part of a distributed Raft cluster, specify this configuration object with the following settings:

      nodeAddress
        The host and port to which to bind the embedded Raft server. Note that the Raft cluster uses a
        separate transport layer for communication that does not integrate with ArtemisMQ messaging services.

        *Default:* not defined

      clusterAddresses
        Must list the addresses of all the members in the cluster. At least one of the members must
        be active and be able to communicate with the cluster leader for the node to join the cluster. If empty, a
        new cluster will be bootstrapped.

        *Default:* not defined

  bftSMaRt
    *(Experimental)* If part of a distributed BFT-SMaRt cluster, specify this configuration object with the following settings:

      replicaId
        The zero-based index of the current replica. All replicas must specify a unique replica id.

        *Default:* not defined

      clusterAddresses
        Must list the addresses of all the members in the cluster. At least one of the members must
        be active and be able to communicate with the cluster leader for the node to join the cluster. If empty, a
        new cluster will be bootstrapped.

        *Default:* not defined

networkParameterAcceptanceSettings
  Optional settings for managing the network parameter auto-acceptance behaviour.
  If not provided then the defined defaults below are used.

  autoAcceptEnabled
    This flag toggles auto accepting of network parameter changes.
    If a network operator issues a network parameter change which modifies only auto-acceptable options and this behaviour is enabled then the changes will be accepted without any manual intervention from the node operator.
    See :doc:`network-map` for more information on the update process and current auto-acceptable parameters.
    Set to ``false`` to disable.

    *Default:* true

  excludedAutoAcceptableParameters
    List of auto-acceptable parameter names to explicitly exclude from auto-accepting.
    Allows a node operator to control the behaviour at a more granular level.

    *Default:* empty list

.. _corda_configuration_file_networkServices:

networkServices
  If the Corda compatibility zone services, both network map and registration (doorman), are not running on the same endpoint
  and thus have different URLs then this option should be used in place of the ``compatibilityZoneURL`` setting.

  **Important: Only one of ``compatibilityZoneURL`` or ``networkServices`` should be used.**

  doormanURL
    Root address of the network registration service.

    *Default:* not defined

  networkMapURL
    Root address of the network map service.

    *Default:* not defined

  pnm
    Optional UUID of the private network operating within the compatibility zone this node should be joining.

    *Default:* not defined

.. _corda_configuration_file_p2pAddress:

p2pAddress
  The host and port on which the node is available for protocol operations over ArtemisMQ.

  In practice the ArtemisMQ messaging services bind to **all local addresses** on the specified port.
  However, note that the host is the included as the advertised entry in the network map.
  As a result the value listed here must be **externally accessible when running nodes across a cluster of machines.**
  If the provided host is unreachable, the node will try to auto-discover its public one.

  *Default:* not defined

rpcAddress (deprecated)
  The address of the RPC system on which RPC requests can be made to the node.
  If not provided then the node will run without RPC.

  **Important: Deprecated. Use rpcSettings instead.**

  *Default:* not defined

.. _corda_configuration_file_rpc_settings:

rpcSettings
  Options for the RPC server exposed by the Node.

  **Important: The RPC SSL certificate is used by RPC clients to authenticate the connection.  The Node operator must provide RPC clients with a truststore containing the certificate they can trust.  We advise Node operators to not use the P2P keystore for RPC.  The node can be run with the "generate-rpc-ssl-settings" command, which generates a secure keystore and truststore that can be used to secure the RPC connection. You can use this if you have no special requirements.**

    address
      host and port for the RPC server binding.

      *Default:* not defined

    adminAddress
      host and port for the RPC admin binding (this is the endpoint that the node process will connect to).

      *Default:* not defined

    standAloneBroker
      boolean, indicates whether the node will connect to a standalone broker for RPC.

      *Default:* false

    useSsl
      boolean, indicates whether or not the node should require clients to use SSL for RPC connections.

      *Default:* false

    ssl
      (mandatory if ``useSsl=true``) SSL settings for the RPC server.

      keyStorePath
        Absolute path to the key store containing the RPC SSL certificate.

      *Default:* not defined

      keyStorePassword
        Password for the key store.

        *Default:* not defined

.. _corda_configuration_file_rpc_users:

rpcUsers
  A list of users who are authorised to access the RPC system.
  Each user in the list is a configuration object with the following fields:

  username
    Username consisting only of word characters (a-z, A-Z, 0-9 and _)

    *Default:* not defined

  password
    The password

    *Default:* not defined

  permissions
    A list of permissions for starting flows via RPC.
    To give the user the permission to start the flow ``foo.bar.FlowClass``, add the string ``StartFlow.foo.bar.FlowClass`` to the list.
    If the list contains the string ``ALL``, the user can start any flow via RPC.
    This value is intended for administrator users and for development.

    *Default:* not defined

security
  Contains various nested fields controlling user authentication/authorization, in particular for RPC accesses.
  See :doc:`clientrpc` for details.

.. _corda_configuration_file_sshd:

sshd
  If provided, node will start internal SSH server which will provide a management shell.
  It uses the same credentials and permissions as RPC subsystem.
  It has one required parameter.

  port
    The port to start SSH server on e.g. ``sshd { port = 2222 }``.

    *Default:* not defined

systemProperties
  An optional map of additional system properties to be set when launching via ``corda.jar`` only.
  Keys and values of the map should be strings. e.g. ``systemProperties = { visualvm.display.name = FooBar }``

  *Default:* not defined

transactionCacheSizeMegaBytes
  Optionally specify how much memory should be used for caching of ledger transactions in memory.

  *Default:* 8 MB plus 5% of all heap memory above 300MB.

tlsCertCrlDistPoint
  CRL distribution point (i.e. URL) for the TLS certificate.
  Default value is NULL, which indicates no CRL availability for the TLS certificate.

  **Important: This needs to be set if crlCheckSoftFail is false (i.e. strict CRL checking is on).**

  *Default:* NULL


tlsCertCrlIssuer
  CRL issuer (given in the X500 name format) for the TLS certificate.
  Default value is NULL, which indicates that the issuer of the TLS certificate is also the issuer of the CRL.

  **Important: If this parameter is set then `tlsCertCrlDistPoint` needs to be set as well.**

  *Default:* NULL


trustStorePassword
  The password to unlock the Trust store file (``<workspace>/certificates/truststore.jks``) containing the Corda network root certificate.
  This is the non-secret value for the development certificates automatically generated during the first node run.

  *Default:* trustpass


useTestClock
  Internal option.

  **Important: Please do not change.**

  *Default:* false


verfierType
  Internal option.

  **Important: Please do not change.**

  *Default:* InMemory

Reference.conf
--------------
A set of default configuration options are loaded from the built-in resource file ``/node/src/main/resources/reference.conf``.
This file can be found in the ``:node`` gradle module of the `Corda repository <https://github.com/corda/corda>`_.
Any options you do not specify in your own ``node.conf`` file will use these defaults.

Here are the contents of the ``reference.conf`` file:

.. literalinclude:: ../../node/src/main/resources/reference.conf
    :language: none


Configuration examples
----------------------

Node configuration hosting the IRSDemo services
````````````````````````````````````````````````````````````````
General node configuration file for hosting the IRSDemo services

.. literalinclude:: example-code/src/main/resources/example-node.conf
    :language: none

Simple notary configuration file
`````````````````````````````````

.. code-block:: none

    myLegalName = "O=Notary Service,OU=corda,L=London,C=GB"
    keyStorePassword = "cordacadevpass"
    trustStorePassword = "trustpass"
    p2pAddress = "localhost:12345"
    rpcSettings {
        useSsl = false
        standAloneBroker = false
        address = "my-corda-node:10003"
        adminAddress = "my-corda-node:10004"
    }
    notary {
        validating = false
    }
    devMode = false
    networkServices {
        doormanURL = "https://cz.example.com"
        networkMapURL = "https://cz.example.com"
    }

Node configuration with diffrent URL for NetworkMap and Doorman
```````````````````````````````````````````````````````````````

Configuring a node where the Corda Compatibility Zone's registration and Network Map services exist on different URLs

.. literalinclude:: example-code/src/main/resources/example-node-with-networkservices.conf
    :language: none


