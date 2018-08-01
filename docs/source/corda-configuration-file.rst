Node configuration
==================

.. contents::

File location
-------------
When starting a node, the ``corda.jar`` file defaults to reading the node's configuration from a ``node.conf`` file in
the directory from which the command to launch Corda is executed. There are two command-line options to override this
behaviour:

* The ``--config-file`` command line option allows you to specify a configuration file with a different name, or at
  different file location. Paths are relative to the current working directory

* The ``--base-directory`` command line option allows you to specify the node's workspace location. A ``node.conf``
  configuration file is then expected in the root of this workspace

If you specify both command line arguments at the same time, the node will fail to start.

Format
------
The Corda configuration file uses the HOCON format which is a superset of JSON. Please visit
`<https://github.com/typesafehub/config/blob/master/HOCON.md>`_ for further details.

Please do NOT use double quotes (``"``) in configuration keys.

Node setup will log `Config files should not contain \" in property names. Please fix: [key]` as an error
when it finds double quotes around keys.
This prevents configuration errors when mixing keys containing ``.`` wrapped with double quotes and without them
e.g.:
The property `"dataSourceProperties.dataSourceClassName" = "val"` in ``reference.conf``
would be not overwritten by the property `dataSourceProperties.dataSourceClassName = "val2"` in ``node.conf``.

By default the node will fail to start in presence of unknown property keys. To alter this behaviour, program line argument
``on-unknown-config-keys`` can be set to ``WARN`` or ``IGNORE``. Default is ``FAIL`` if unspecified.

Defaults
--------
A set of default configuration options are loaded from the built-in resource file ``/node/src/main/resources/reference.conf``.
This file can be found in the ``:node`` gradle module of the `Corda repository <https://github.com/corda/corda>`_. Any
options you do not specify in your own ``node.conf`` file will use these defaults.

Here are the contents of the ``reference.conf`` file:

.. literalinclude:: ../../node/src/main/resources/reference.conf
    :language: javascript

Fields
------
The available config fields are listed below. ``baseDirectory`` is available as a substitution value and contains the
absolute path to the node's base directory.

:myLegalName: The legal identity of the node. This acts as a human-readable alias to the node's public key and can be used with
    the network map to look up the node's info. This is the name that is used in the node's certificates (either when requesting them
    from the doorman, or when auto-generating them in dev mode). At runtime, Corda checks whether this name matches the
    name in the node's certificates.

:keyStorePassword: The password to unlock the KeyStore file (``<workspace>/certificates/sslkeystore.jks``) containing the
    node certificate and private key.

    .. note:: This is the non-secret value for the development certificates automatically generated during the first node run.
        Longer term these keys will be managed in secure hardware devices.

:trustStorePassword: The password to unlock the Trust store file (``<workspace>/certificates/truststore.jks``) containing
    the Corda network root certificate. This is the non-secret value for the development certificates automatically
    generated during the first node run.

    .. note:: Longer term these keys will be managed in secure hardware devices.

:crlCheckSoftFail: This is a boolean flag that when enabled (i.e. `true` value is set) the certificate revocation list (CRL) checking will use the soft fail mode.
                  The soft fail mode allows the revocation check to succeed if the revocation status cannot be determined because of a network error.
                  If this parameter is set to `false` the rigorous CRL checking takes place, meaning that each certificate in the
                  certificate path being checked needs to have the CRL distribution point extension set and pointing to a URL serving a valid CRL.

.. _databaseConfiguration:

:database: Database configuration:

        :transactionIsolationLevel: Transaction isolation level as defined by the ``TRANSACTION_`` constants in
            ``java.sql.Connection``, but without the ``TRANSACTION_`` prefix. Defaults to REPEATABLE_READ.
        :exportHibernateJMXStatistics: Whether to export Hibernate JMX statistics (caution: expensive run-time overhead)

:dataSourceProperties: This section is used to configure the jdbc connection and database driver used for the nodes persistence.
    Currently the defaults in ``/node/src/main/resources/reference.conf`` are as shown in the first example. This is currently
    the only configuration that has been tested, although in the future full support for other storage layers will be validated.

:h2Port: Deprecated. Use ``h2Settings`` instead.

:h2Settings:  Sets the H2 JDBC server host and port. See :doc:`node-database-access-h2`. For non-localhost address the database passowrd needs to be set in ``dataSourceProperties``.

:messagingServerAddress: The address of the ArtemisMQ broker instance. If not provided the node will run one locally.

:p2pAddress: The host and port on which the node is available for protocol operations over ArtemisMQ.

    .. note:: In practice the ArtemisMQ messaging services bind to all local addresses on the specified port. However,
        note that the host is the included as the advertised entry in the network map. As a result the value listed
        here must be externally accessible when running nodes across a cluster of machines. If the provided host is unreachable,
        the node will try to auto-discover its public one.

:flowTimeout: When a flow implementing the ``TimedFlow`` interface does not complete in time, it is restarted from the
    initial checkpoint. Currently only used for notarisation requests: if a notary replica dies while processing a notarisation request,
    the client flow eventually times out and gets restarted. On restart the request is resent to a different notary replica
    in a round-robin fashion (assuming the notary is clustered).

        :timeout: The initial flow timeout period, e.g. `30 seconds`.
        :maxRestartCount: Maximum number of times the flow will restart before resulting in an error.
        :backoffBase: The base of the exponential backoff, `t_{wait} = timeout * backoffBase^{retryCount}`.

:rpcAddress: (Deprecated) The address of the RPC system on which RPC requests can be made to the node. If not provided then the node will run without RPC. This is now deprecated in favour of the ``rpcSettings`` block.

:rpcSettings: Options for the RPC server exposed by the Node.

        :address: host and port for the RPC server binding.
        :adminAddress: host and port for the RPC admin binding (this is the endpoint that the node process will connect to).
        :standAloneBroker: (optional) boolean, indicates whether the node will connect to a standalone broker for RPC, defaulted to ``false``.
        :useSsl: (optional) boolean, indicates whether or not the node should require clients to use SSL for RPC connections, defaulted to ``false``.
        :ssl: (mandatory if ``useSsl=true``) SSL settings for the RPC server.

                :keyStorePath: Absolute path to the key store containing the RPC SSL certificate.
                :keyStorePassword: Password for the key store.

        .. note:: The RPC SSL certificate is used by RPC clients to authenticate the connection.
            The Node operator must provide RPC clients with a truststore containing the certificate they can trust.
            We advise Node operators to not use the P2P keystore for RPC.
            The node ships with a command line argument "--just-generate-rpc-ssl-settings", which generates a secure keystore
            and truststore that can be used to secure the RPC connection. You can use this if you have no special requirements.


:security: Contains various nested fields controlling user authentication/authorization, in particular for RPC accesses. See
    :doc:`clientrpc` for details.

:notary: Optional configuration object which if present configures the node to run as a notary. If part of a Raft or BFT SMaRt
    cluster then specify ``raft`` or ``bftSMaRt`` respectively as described below. If a single node notary then omit both.

    :validating: Boolean to determine whether the notary is a validating or non-validating one.

    :serviceLegalName: If the node is part of a distributed cluster, specify the legal name of the cluster. At runtime, Corda
        checks whether this name matches the name of the certificate of the notary cluster.

    :raft: If part of a distributed Raft cluster specify this config object, with the following settings:

        :nodeAddress: The host and port to which to bind the embedded Raft server. Note that the Raft cluster uses a
            separate transport layer for communication that does not integrate with ArtemisMQ messaging services.

        :clusterAddresses: Must list the addresses of all the members in the cluster. At least one of the members must
            be active and be able to communicate with the cluster leader for the node to join the cluster. If empty, a
            new cluster will be bootstrapped.

    :bftSMaRt: If part of a distributed BFT-SMaRt cluster specify this config object, with the following settings:

        :replicaId: The zero-based index of the current replica. All replicas must specify a unique replica id.

        :clusterAddresses: Must list the addresses of all the members in the cluster. At least one of the members must
            be active and be able to communicate with the cluster leader for the node to join the cluster. If empty, a
            new cluster will be bootstrapped.

    :custom: If `true`, will load and install a notary service from a CorDapp. See :doc:`tutorial-custom-notary`.

    Only one of ``raft``, ``bftSMaRt`` or ``custom`` configuration values may be specified.

:rpcUsers: A list of users who are authorised to access the RPC system. Each user in the list is a config object with the
    following fields:

    :username: Username consisting only of word characters (a-z, A-Z, 0-9 and _)
    :password: The password
    :permissions: A list of permissions for starting flows via RPC. To give the user the permission to start the flow
        ``foo.bar.FlowClass``, add the string ``StartFlow.foo.bar.FlowClass`` to the list. If the list
        contains the string ``ALL``, the user can start any flow via RPC. This value is intended for administrator
        users and for development.

:devMode: This flag sets the node to run in development mode. On startup, if the keystore ``<workspace>/certificates/sslkeystore.jks``
    does not exist, a developer keystore will be used if ``devMode`` is true. The node will exit if ``devMode`` is false
    and the keystore does not exist. ``devMode`` also turns on background checking of flow checkpoints to shake out any
    bugs in the checkpointing process.
    Also, if ``devMode`` is true, Hibernate will try to automatically create the schema required by Corda
    or update an existing schema in the SQL database; if ``devMode`` is false, Hibernate will simply validate the existing schema,
    failing on node start if the schema is either not present or not compatible.
    If no value is specified in the node config file, the node will attempt to detect if it's running on a developer machine and set ``devMode=true`` in that case.
    This value can be overridden from the command line using the ``--dev-mode`` option.

:detectPublicIp: This flag toggles the auto IP detection behaviour, it is enabled by default. On startup the node will
    attempt to discover its externally visible IP address first by looking for any public addresses on its network
    interfaces, and then by sending an IP discovery request to the network map service. Set to ``false`` to disable.

:compatibilityZoneURL: The root address of Corda compatibility zone network management services, it is used by the Corda node to register with the network and
    obtain Corda node certificate, (See :doc:`permissioning` for more information.) and also used by the node to obtain network map information. Cannot be
    set at the same time as the ``networkServices`` option.

:networkServices: If the Corda compatibility zone services, both network map and registration (doorman), are not running on the same endpoint
    and thus have different URLs then this option should be used in place of the ``compatibilityZoneURL`` setting.

    :doormanURL: Root address of the network registration service.
    :networkMapURL: Root address of the network map service.

        .. note:: Only one of ``compatibilityZoneURL`` or ``networkServices`` should be used.

:devModeOptions: Allows modification of certain ``devMode`` features

    :allowCompatibilityZone: Allows a node configured to operate in development mode to connect to a compatibility zone.

        .. note:: This is an unsupported configuration.

:jvmArgs: An optional list of JVM args, as strings, which replace those inherited from the command line when launching via ``corda.jar``
    only. e.g. ``jvmArgs = [ "-Xmx220m", "-Xms220m", "-XX:+UseG1GC" ]``

:systemProperties: An optional map of additional system properties to be set when launching via ``corda.jar`` only.  Keys and values
    of the map should be strings. e.g. ``systemProperties = { visualvm.display.name = FooBar }``

:jarDirs: An optional list of file system directories containing JARs to include in the classpath when launching via ``corda.jar`` only.
    Each should be a string.  Only the JARs in the directories are added, not the directories themselves.  This is useful
    for including JDBC drivers and the like. e.g. ``jarDirs = [ '${baseDirectory}/lib' ]`` (Note that you have to use the ``baseDirectory``
    substitution value when pointing to a relative path)

:sshd: If provided, node will start internal SSH server which will provide a management shell. It uses the same credentials and permissions as RPC subsystem. It has one required parameter.

    :port: The port to start SSH server on e.g. ``sshd { port = 2222 }``.

:jmxMonitoringHttpPort: If set, will enable JMX metrics reporting via the Jolokia HTTP/JSON agent on the corresponding port.
    Default Jolokia access url is http://127.0.0.1:port/jolokia/

:transactionCacheSizeMegaBytes: Optionally specify how much memory should be used for caching of ledger transactions in memory.
            Otherwise defaults to 8MB plus 5% of all heap memory above 300MB.

:attachmentContentCacheSizeMegaBytes: Optionally specify how much memory should be used to cache attachment contents in memory.
            Otherwise defaults to 10MB

:attachmentCacheBound: Optionally specify how many attachments should be cached locally. Note that this includes only the key and
            metadata, the content is cached separately and can be loaded lazily. Defaults to 1024.

:extraNetworkMapKeys: An optional list of private network map UUIDs. Your node will fetch the public network and private network maps based on
            these keys. Private network UUID should be provided by network operator and lets you see nodes not visible on public network.

            .. note:: This is temporary feature for onboarding network participants that limits their visibility for privacy reasons.

:tlsCertCrlDistPoint: CRL distribution point (i.e. URL) for the TLS certificate. Default value is NULL, which indicates no CRL availability for the TLS certificate.

                      .. note:: This needs to be set if crlCheckSoftFail is false (i.e. strict CRL checking is on).

:tlsCertCrlIssuer: CRL issuer (given in the X500 name format) for the TLS certificate. Default value is NULL,
                   which indicates that the issuer of the TLS certificate is also the issuer of the CRL.

                   .. note:: If this parameter is set then `tlsCertCrlDistPoint` needs to be set as well.

:flowMonitorPeriodMillis: ``Duration`` of the period suspended flows waiting for IO are logged. Default value is ``60 seconds``.

:flowMonitorSuspensionLoggingThresholdMillis: Threshold ``Duration`` suspended flows waiting for IO need to exceed before they are logged. Default value is ``60 seconds``.

Examples
--------

General node configuration file for hosting the IRSDemo services:

.. literalinclude:: example-code/src/main/resources/example-node.conf

Simple notary configuration file:

.. parsed-literal::

    myLegalName : "O=Notary Service,OU=corda,L=London,C=GB"
    keyStorePassword : "cordacadevpass"
    trustStorePassword : "trustpass"
    p2pAddress : "localhost:12345"
    rpcSettings = {
        useSsl = false
        standAloneBroker = false
        address : "my-corda-node:10003"
        adminAddress : "my-corda-node:10004"
    }
    notary : {
        validating : false
    }
    devMode : false
    compatibilityZoneURL : "https://cz.corda.net"

Configuring a node where the Corda Compatibility Zone's registration and Network Map services exist on different URLs

.. literalinclude:: example-code/src/main/resources/example-node-with-networkservices.conf

Fields Override
---------------
JVM options or environmental variables prefixed with ``corda.`` can override ``node.conf`` fields.
Provided system properties can also set values for absent fields in ``node.conf``.

This is an example of adding/overriding the keyStore password :

.. sourcecode:: shell

    java -Dcorda.rpcSettings.ssl.keyStorePassword=mypassword -jar node.jar

CRL Configuration
-----------------
The Corda Network provides an endpoint serving an empty certificate revocation list for the TLS-level certificates.
This is intended for deployments that do not provide a CRL infrastructure but still require a strict CRL mode checking.
In such a case use the following URL in `tlsCertCrlDistPoint` option configuration:

    .. sourcecode:: kotlin

        "https://crl.cordaconnect.org/cordatls.crl"

Together with the above configuration `tlsCertCrlIssuer` option needs to be set to the following value:

    .. sourcecode:: kotlin

        "C=US, L=New York, O=R3 HoldCo LLC, OU=Corda, CN=Corda Root CA"

This set-up ensures that the TLS-level certificates are embedded with the CRL distribution point referencing the CRL issued by R3.
In cases where a proprietary CRL infrastructure is provided those values need to be changed accordingly.
