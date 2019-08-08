Platform support matrix
=======================

Our supported Operating System platforms are a subset of those supported by `Java <http://www.oracle.com/technetwork/java/javase/certconfig-2095354.html>`_.

Production use of |release| is only supported on Linux OS, see details below.

JDK support
~~~~~~~~~~~
|release| has been tested and verified to work with **Oracle JDK 8 JVM 8u171\+** and **Azul Zulu Enterprise 8**, downloadable from
`Azul Systems <https://www.azul.com/downloads/azure-only/zulu/>`_.

.. note:: On previous versions of Corda only the **Oracle JDK 8 JVM 8u171\+** is supported.

Other distributions of the `OpenJDK <https://openjdk.java.net/>`_ are not officially supported but should be compatible with |release|.

.. warning:: In accordance with the `Oracle Java SE Support Roadmap <https://www.oracle.com/technetwork/java/java-se-support-roadmap.html>`_
   which outlines the end of public updates of Java SE 8 for commercial use, please ensure you have the correct Java support contract in place
   for your deployment needs.

Operating systems supported in production
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

+-------------------------------+------------------+-----------+
| Platform                      | CPU Architecture | Versions  |
+===============================+==================+===========+
| Red Hat Enterprise Linux      | x86-64           | 7.x,      |
|                               |                  | 6.x       |
+-------------------------------+------------------+-----------+
| Suse Linux Enterprise Server  | x86-64           | 12.x,     |
|                               |                  | 11.x      |
+-------------------------------+------------------+-----------+
| Ubuntu Linux                  | x86-64           | 16.04,    |
|                               |                  | 18.04     |
+-------------------------------+------------------+-----------+
| Oracle Linux                  | x86-64           | 7.x,      |
|                               |                  | 6.x       |
+-------------------------------+------------------+-----------+

Operating systems supported in development
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

+-------------------------------+------------------+-----------+
| Platform                      | CPU Architecture | Versions  |
+===============================+==================+===========+
| Microsoft Windows             | x86-64           | 10,       |
|                               |                  | 8.x       |
+-------------------------------+------------------+-----------+
| Microsoft Windows Server      | x86-64           | 2016,     |
|                               |                  | 2012 R2,  |
|                               |                  | 2012      |
+-------------------------------+------------------+-----------+
| Apple macOS                   | x86-64           | 10.9 and  |
|                               |                  | above     |
+-------------------------------+------------------+-----------+

Databases
~~~~~~~~~

+-------------------------------+------------------+------------------+--------------------+
| Vendor                        | CPU Architecture | Versions         | JDBC Driver        |
+===============================+==================+==================+====================+
| Microsoft                     | x86-64           | Azure SQL,       | Microsoft JDBC     |
|                               |                  | SQL Server 2017  | Driver 6.4         |
+-------------------------------+------------------+------------------+--------------------+
| Oracle                        | x86-64           | 11gR2            | Oracle JDBC 6      |
+-------------------------------+------------------+------------------+--------------------+
| Oracle                        | x86-64           | 12cR2            | Oracle JDBC 8      |
+-------------------------------+------------------+------------------+--------------------+
| PostgreSQL                    | x86-64           | 9.6              | PostgreSQL JDBC    |
|                               |                  |                  | Driver 42.1.4      |
+-------------------------------+------------------+------------------+--------------------+

Hardware Security Modules (HSM)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

+-------------------------------+----------------------------+----------------------------+---------------------------------------+
| Device                        |Legal Identity & CA keys    | TLS keys                   | Confidential Identities keys          |
+===============================+============================+============================+=======================================+
| Utimaco SecurityServer Se Gen2| * Firmware version 4.21.1  | * Firmware version 4.21.1  | Not supported                         |
|                               | * Driver version 4.21.1    | * Driver version 4.21.1    |                                       |
+-------------------------------+----------------------------+----------------------------+---------------------------------------+
| Gemalto Luna                  | * Firmware version 7.0.3   | * Firmware version 7.0.3   | Not supported                         |
|                               | * Driver version 7.3       | * Driver version 7.3       |                                       |
+-------------------------------+----------------------------+----------------------------+---------------------------------------+
| FutureX Excrypt SSP9000       | * Firmware version 3.1     | * Firmware version 3.1     | Not supported                         |
|                               | * Driver version 3.1       | * Driver version 3.1       |                                       |
+-------------------------------+----------------------------+----------------------------+---------------------------------------+
| Azure Key Vault               | * Driver version 1.1.1     | * Driver version 1.1.1     | Not supported                         |
|                               |                            |                            |                                       |
+-------------------------------+----------------------------+----------------------------+---------------------------------------+
| Securosys PrimusX             | * Firmware version 2.7.4   | * Firmware version 2.7.4   | * Firmware version 2.7.4              |
|                               | * Driver version 1.8.2     | * Driver version 1.8.2     | * Driver version 1.8.2                |
+-------------------------------+----------------------------+----------------------------+---------------------------------------+
