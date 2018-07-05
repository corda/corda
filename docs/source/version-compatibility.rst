Corda and Corda Enterprise compatibility
========================================

Corda Enterprise 3.0 provides a baseline for wire stability and compatibility with future versions of Corda Enterprise, and open-source releases of Corda starting from version 3.0.

Future versions of Corda Enterprise will be backward compatible with Corda Enterprise 3.0:

 * Corda Enterprise 3.0 nodes can be upgraded to future version of Corda Enterprise. The upgrade will preserve transaction, configuration and other data.

 * Corda Enterprise 3.0 nodes will be able to transact with nodes running future versions of Corda Enterprise, providing the CorDapp is compatible with and between platform versions.

 * Future versions of Corda Enterprise will be able to run CorDapps developed for, and packaged on Corda Enterprise 3.0.

Corda Enterprise 3.0 can be used in mixed-version/mixed-distribution networks seamlessly transacting with nodes running Corda 3.x and future versions.

 * Corda Enterprise 3.0 nodes can transact with nodes running Corda 3.0 and future versions, providing the CorDapp is compatible with and between platform versions and distributions.

 * CorDapps originally written for Corda 3.x are API compatible with Corda Enterprise 3.0 and future versions, developers can switch their IDE to Corda Enterprise 3.0 without any code changes in their CorDapp.

 * Corda Enterprise 3.0 nodes can run, without recompilation, CorDapps developed on and packaged for Corda 3.x.

.. note:: These compatibility commitments are subject to the standard Corda Enterprise software support policy.

.. role:: grey

+------------------------------------------------+-------------+---------------+---------------+------------------+-----------------+-----------------+---------------------+
| Compatibility with Corda Enterprise 3.0        | :grey:`DP3` | :grey:`DP2`   | :grey:`DP1`   | Corda 3.x        | :grey:`Corda 2` | :grey:`Corda 1` | :grey:`Corda pre 1` |
+================================================+=============+===============+===============+==================+=================+=================+=====================+
| **API compatibility**, i.e. CorDapps developed | :grey:`Yes` | :grey:`Yes`   | :grey:`Yes`   | Yes              | :grey:`Yes`     | :grey:`Yes`     | :grey:`No`          |
| for this Corda version can be compiled and run |             |               |               |                  |                 |                 |                     |
| on Corda Enterprise 3.0 nodes                  |             |               |               |                  |                 |                 |                     |
+------------------------------------------------+-------------+---------------+---------------+------------------+-----------------+-----------------+---------------------+
| **Binary compatibility**, i.e. CorDapps        | :grey:`Yes` | :grey:`Yes`   | :grey:`Yes`   | Yes              | :grey:`Yes`     | :grey:`Yes`     | :grey:`No`          |
| compiled on this Corda version can be run on   |             |               |               |                  |                 |                 |                     |
| Corda Enterprise 3.0 nodes                     |             |               |               |                  |                 |                 |                     |
+------------------------------------------------+-------------+---------------+---------------+------------------+-----------------+-----------------+---------------------+
| **Network compatibility**, i.e., nodes running | :grey:`No*` | :grey:`Yes`   | :grey:`Yes`   | Yes              | :grey:`No`      | :grey:`No`      | :grey:`No`          |
| this Corda version can transact with Corda     |             |               |               |                  |                 |                 |                     |
| Enterprise 3.0 nodes                           |             |               |               |                  |                 |                 |                     |
+------------------------------------------------+-------------+---------------+---------------+------------------+-----------------+-----------------+---------------------+
| **RPC compatibility**, i.e, a client           | :grey:`Yes` | :grey:`Yes`   | :grey:`Yes`   | No               | :grey:`No`      | :grey:`No`      | :grey:`No`          |
| application developed for this Corda version   |             |               |               |                  |                 |                 |                     |
| can interact via RPC with a CorDapp running on |             |               |               |                  |                 |                 |                     |
| the Corda Enterprise 3.0 node                  |             |               |               |                  |                 |                 |                     |
+------------------------------------------------+-------------+---------------+---------------+------------------+-----------------+-----------------+---------------------+
| Samples and community apps from                | :grey:`Yes` | :grey:`Yes`   | :grey:`Yes`   | Yes              | :grey:`Yes`     | :grey:`Yes`     | :grey:`No`          |
| https://www.corda.net/samples/ for this Corda  |             |               |               |                  |                 |                 |                     |
| version can be compiled and run on Corda       |             |               |               |                  |                 |                 |                     |
| Enterprise 3.0 nodes                           |             |               |               |                  |                 |                 |                     |
+------------------------------------------------+-------------+---------------+---------------+------------------+-----------------+-----------------+---------------------+
| Bootstrapper tool from this Corda version can  | :grey:`No*` | :grey:`Yes`   | :grey:`Yes`   | No               | :grey:`No`      | :grey:`No`      | :grey:`No`          |
| be used to build Corda Enterprise 3.0 test     |             |               |               |                  |                 |                 |                     |
| networks                                       |             |               |               |                  |                 |                 |                     |
+------------------------------------------------+-------------+---------------+---------------+------------------+-----------------+-----------------+---------------------+

.. note:: Greyed out releases are out of support.

    Cells denoted with asterisk (*) refer to development-mode only. The incompatibility is caused by a change to the truststore for development-mode certificates between
    Developer Preview 3 (DP3) and Corda Enterprise 3.0. In other words, DP3 is network compatible with Corda Enterprise 3.0 provided that development-mode is disabled.

