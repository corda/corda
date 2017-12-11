Access security settings
========================

Access to node functionalities via SSH or RPC is protected by an authentication and authorisation policy.

The field ``security`` in ``node.conf`` exposes various sub-fields related to authentication/authorisation specifying:

 * The data source providing credentials and permissions for users (e.g.: a remote RDBMS)
 * An optional password encryption method.
 * An optional caching of users data from Node side.

.. warning:: Specifying both ``rpcUsers`` and ``security`` fields in ``node.conf`` is considered an illegal setting and
   rejected by the node at startup since ``rpcUsers`` is effectively deprecated in favour of ``security.authService``.

**Example 1:** connect to remote RDBMS for credentials/permissions, with encrypted user passwords and
caching on node-side:

.. container:: codeset

    .. sourcecode:: groovy

        security = {
            authService = {
                dataSource = {
                    type = "DB",
                    passwordEncryption = "SHIRO_1_CRYPT",
                    connection = {
                       jdbcUrl = "<jdbc connection string>"
                       username = "<db username>"
                       password = "<db user password>"
                       driverClassName = "<JDBC driver>"
                    }
                }
                options = {
                     cache = {
                        expiryTimeSecs = 120
                        capacity = 10000
                     }
                }
            }
        }

**Example 2:** list of user credentials and permissions hard-coded in ``node.conf``

.. container:: codeset

    .. sourcecode:: groovy

        security = {
            authService = {
                dataSource = {
                    type = "INMEMORY",
                    users =[
                        {
                            username = "user1"
                            password = "password"
                            permissions = [
                                "StartFlow.net.corda.flows.ExampleFlow1",
                                "StartFlow.net.corda.flows.ExampleFlow2",
                                ...
                            ]
                        },
                        ...
                    ]
                }
            }
        }

Let us look in more details at the structure of ``security.authService``:

Authentication/authorisation data
---------------------------------

The ``dataSource`` field defines the data provider supplying credentials and permissions for users. The ``type``
subfield identify the type of data provider, currently supported one are:

 * **INMEMORY:** a list of user credentials and permissions hard-coded in configuration in the ``users`` field
   (see example 2 above)

 * **DB:** An external RDBMS accessed via the JDBC connection described by ``connection``. The current implementation
   expect the database to store data according to the following schema:

           - Table ``users`` containing columns ``username`` and ``password``.
             The ``username`` column *must have unique values*.
           - Table ``user_roles`` containing columns ``username`` and ``role_name`` associating a user to a set of *roles*
           - Table ``roles_permissions`` containing columns ``role_name`` and ``permission`` associating a role to a set of
             permission strings

   Note in particular how in the DB case permissions are assigned to _roles_ rather than individual users.
   Also, there is no prescription on the SQL type of the columns (although in our tests we defined ``username`` and
   ``role_name`` of SQL type ``VARCHAR`` and ``password`` of ``TEXT`` type) and it is allowed to put additional columns
   besides the one expected by the implementation.

Password encryption
-------------------

Storing passwords in plain text is discouraged in production systems aiming for high security requirements. We support
reading passwords stored using the Apache Shiro fully reversible Modular Crypt Format, specified in the documentation
of ``org.apache.shiro.crypto.hash.format.Shiro1CryptFormat``.

Password are assumed in plain format by default. To specify an encryption it is necessary to use the field:

.. container:: codeset

    .. sourcecode:: groovy

        passwordEncryption = SHIRO_1_CRYPT

Hash encrypted password based on the Shiro1CryptFormat can be produced with the `Apache Shiro Hasher tool  <https://shiro.apache.org/command-line-hasher.html>`_

Cache
-----

Adding a cache layer on top of an external provider of users credentials and permissions can significantly benefit
performances in some cases, with the disadvantage of introducing a latency in the propagation of changes to the data.

Caching of users data is disabled by default, it can be enabled by defining the ``options.cache`` field, like seen in
the examples above:

.. container:: codeset

    .. sourcecode:: groovy

        options = {
             cache = {
                expiryTimeSecs = 120
                capacity = 10000
             }
        }

This will enable an in-memory cache with maximum capacity (number of entries) and maximum life time of entries given by
respectively the values set by the ``capacity`` and ``expiryTimeSecs`` fields.




