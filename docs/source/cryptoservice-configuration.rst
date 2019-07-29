HSM support for legal identity keys
===================================

By default, the private keys that belong to the node CA and legal identity are stored in a key store file in the node's certificates directory. Users may wish to instead store this key in a hardware security module (HSM) or similar. For this purpose, Corda Enterprise supports HSMs by `Utimaco <https://hsm.utimaco.com>`_, `Gemalto <https://www.gemalto.com>`_, and `Azure KeyVault <https://azure.microsoft.com/en-gb/services/key-vault>`_.

Note that only the private and public key of node CA and the legal identity are stored this way. The certificate chain is still stored in a file-based key store.

.. contents::

Configuration
-------------

As mentioned in the description of the configuration file (:doc:`corda-configuration-file`), the ``node.conf`` has two relevant fields, ``cryptoServiceName`` and ``cryptoServiceConf``.

.. warning:: The file containing the configuration for the HSM (referenced by the ``cryptoServiceConf`` field) contains sensitive information. So, we strongly advise using the Configuration Obfuscator tool for it, as documented here: :doc:`tools-config-obfuscator`

Utimaco
-------

Corda Enterprise nodes can be configured to store their legal identity keys in `Utimaco's SecurityServer Se Gen2 <https://hsm.utimaco.com/products-hardware-security-modules/general-purpose-hsm/securityserver-se-gen2/>`_ running firmware version 4.21.1.

In the ``node.conf``, the ``cryptoServiceName`` needs to be set to "UTIMACO", and ``cryptoServiceConf`` should contain the path to the configuration for Utimaco, as shown below.

.. parsed-literal::

    cryptoServiceName : "UTIMACO"
    cryptoServiceConf : "utimaco.conf"

The configuration file for Utimaco has the fields described below. The entries are similar to the ones described in the documentation for the CryptoServer JCE provider, and you should refer to this documentation for more details. We cannot link to the documentation here, but you should have received a copy which contains the file ``JCE-Documentation.html``.

:host: address of the device or simulator.

:port: port of the device or simulator.

:connectionTimeout: (optional) timeout when establishing connection to the device, in milliseconds. The default is 30000.

:keepSessionAlive: (optional) boolean, false by default. If set to false the connection to the device will terminate after 15 minutes. The node will attempt to automatically re-establish the connection.

:keyGroup: The key group to be used when generating keys.

:keySpecifier: The key specifier to be used when reading keys. The default is "*".

:keyOverride: (optional) boolean, the default is false.

:keyExport: (optional) boolean, the default is false.

:keyGenMechanism: the key generation mechanism to be used when generating keys.

:authThreshold: (optional) integer, 1 by default.

:username: the username.

:password: the login password, or, if logging in with a key file, the password for the key file.

:keyFile: (optional) key file for file-based log in.

Example configuration file:

.. parsed-literal::

      host: "127.0.0.1"
      port: 3001
      connectionTimeout: 60000
      keepSessionAlive: true
      keyGroup: "*"
      keySpecifier: 2
      keyOverride: true
      keyExport: false
      keyGenMechanism: 4
      authThreshold: 1
      username: user
      password: "my-password"

In addition to the configuration, the node needs to access binaries provided by Utimaco. The ``CryptoServerJCE.jar`` for release 4.21.1, which can be obtained from Utimaco, needs to be placed in the node's drivers folder.

Gemalto Luna
------------

Corda Enterprise nodes can be configured to store their legal identity keys in `Gemalto Luna <https://safenet.gemalto.com/data-encryption/hardware-security-modules-hsms/safenet-network-hsm>`_ HSMs running firmware version 7.3.

In the ``node.conf``, the ``cryptoServiceName`` needs to be set to "GEMALTO_LUNA", and ``cryptoServiceConf`` should contain the path to a configuration file, the content of which is explained further down.

.. parsed-literal::

    cryptoServiceName : "GEMALTO_LUNA"
    cryptoServiceConf : "gemalto.conf"

The configuration file for Gemalto Luna has two fields. The ``keyStore`` field needs to specify a slot or partition. The ``password`` field contains the password associated with the slot or partition.

:keyStore: specify the slot or partition.

:password: the password associated with the slot or partition.

Example configuration file:

.. parsed-literal::

      keyStore: "tokenlabel:my-partition"
      password: "my-password"

Note that the Gemalto's JCA provider (version 7.3) has to be installed as described in the documentation for the Gemalto Luna.

Futurex
-------

Corda Enterprise nodes can be configured to store their legal identity keys in `FutureX Excrypt SSP9000 <https://www.futurex.com/products/excrypt-ssp9000>`_ HSMs running firmware version 3.1.

In the ``node.conf``, the ``cryptoServiceName`` needs to be set to "FUTUREX", and ``cryptoServiceConf`` should contain the path to a configuration file, the content of which is explained further down.

.. parsed-literal::

    cryptoServiceName : "FUTUREX"
    cryptoServiceConf : "futurex.conf"

The configuration file for Futurex has one field, ``credentials`` that contains the password (PIN) required to authenticate with the HSM.

Example configuration file:

.. parsed-literal::

      credentials: "password"

When starting Corda the environment variables ``FXPKCS11_CFG`` and ``FXPKCS11_MODULE`` need to be set as detailed in Futurex's documentation.
Corda must be running with the system property ``java.library.path`` pointing to the directory that contains the FutureX binaries (e.g. ``libfxjp11.so`` for Linux).
Additionaly, The JAR containing the Futurex JCA provider (version 3.1) must be put on the class path, or copied to the node's ``drivers`` directory.


Azure KeyVault
--------------

In the ``node.conf``, the ``cryptoServiceName`` needs to be set to "AZURE_KEY_VAULT" and ``cryptoServiceConf`` should cointain the path to the configuration for Azure KeyVault, as shown below.

.. parsed-literal::

    cryptoServiceName: "AZURE_KEY_VAULT"
    cryptoServiceConf: "az_keyvault.conf"

The configuration file for Azure KeyVault contains the fields listed below. For details refer to the `Azure KeyVault documentation <https://docs.microsoft.com/en-gb/azure/key-vault>`_.

:path: path to the key store for login. Note that the .pem file that belongs to your service principal needs to be created to pkcs12. One way of doing this is by using openssl: ``openssl pkcs12 -export -in /home/username/tmpdav8oje3.pem -out keyvault_login.p12``.

:alias: alias of the key used for login.

:password: password to the key store.

:clientId: the client id for the login.

:keyVaultURL: the URL of the key vault.

:protection: If set to "HARDWARE", 'hard' keys will be used, if set to "SOFTWARE", 'soft' keys will be used `as described in the Azure KeyVault documentation <https://docs.microsoft.com/en-gb/azure/key-vault/about-keys-secrets-and-certificates#key-vault-keys>`_.

Example configuration file:

.. parsed-literal::

    path: keyvault_login.p12
    alias: "my-alias"
    password: "my-password"
    keyVaultURL: "https://<mykeyvault>.vault.azure.net/"
    clientId: "a3d72387-egfa-4bc2-9cba-b0b27c63540e"
    protection: "HARDWARE"

Securosys Primus X
------------

Corda Enterprise nodes can be configured to store their legal identity keys in `Securosys Primus X <https://www.securosys.ch/product/high-availability-high-performance-hardware-security-module>`_ HSMs running firmware version 2.7.3.

In the ``node.conf``, the ``cryptoServiceName`` needs to be set to "PRIMUS_X", and ``cryptoServiceConf`` should contain the path to a configuration file, the content of which is explained further down.

.. parsed-literal::

    cryptoServiceName : "PRIMUS_X"
    cryptoServiceConf : "primusx.conf"

The configuration file for Securosys Primus X has the following fields:

:host: address of the device

:port: port of the device

:username: the username of the account

:password: the login password of the account

Example configuration file:

.. parsed-literal::

      host: "some-address.securosys.ch"
      port: 2000
      username: "my-username"
      password: "my-password"

In addition to the configuration, the Securosys' Primus X JCA provider (version 1.8.0) needs to be placed in the node's drivers folder.