Corda and Corda Enterprise compatibility
========================================

Corda Enterprise 4.0 guarantees the wire stability and compatibility baseline introduced in version 3.0 of the open-source release of Corda
is maintained with future versions of Corda Enterprise.

Future versions of Corda Enterprise will be backward compatible with Corda Enterprise 4.0:

 * Corda Enterprise 4.0 nodes can be upgraded to the next major version of Corda Enterprise (5.x). The upgrade will preserve transaction, configuration and other data.

 * Corda Enterprise 4.0 nodes will be able to transact with nodes running previous (3.x) and next (5.x) versions of Corda Open Source and Enterprise,
   providing the CorDapp is compatible with and between platform versions.

 * The next major version of Corda Enterprise (5.x) will be able to run CorDapps developed for, and packaged on Corda Enterprise 4.0.

Corda Enterprise 4.0 can be used in mixed-version/mixed-distribution networks seamlessly transacting with nodes running on a minimum platform version of 4.
See :doc:`versioning` for further information.

 * Corda Enterprise 4.0 nodes can transact with nodes running Corda 4.0 and future versions, providing the CorDapp is compatible with and between platform versions and distributions.

 * CorDapps originally written for Corda 4.x are API compatible with Corda Enterprise 4.0 and future versions.
   Developers can switch their IDE to Corda Enterprise 4.0 without any code changes in their CorDapp.

   .. note:: The reverse is not true. We do not commit that CorDapps compiled against Corda Enterprise will run on Corda (open source).

 * Corda Enterprise 4.0 nodes can run, without recompilation, CorDapps developed on and packaged for Corda 4.x.
   However, owing to advanced features in Corda Enterprise, such as database migration scripting support, we anticipate application developers
   will build their CorDapp kernels (contracts, states) against Corda but supply separate Workflow Jars (that depend on the same kernel)
   that are optimised for the two distributions. See :ref:`cordapp_separation_ref` for further information.

.. note:: These compatibility commitments are subject to the standard Corda Enterprise software support policy.

.. role:: grey

+-------------------------------------------------+-------------+-----------------------+---------------+
| Compatibility with Corda Enterprise 4.0         | Corda 4.x   | Enterprise Corda 3.x  | Corda 3.x     |
+=================================================+=============+=======================+===============+
| **API compatibility**, i.e. CorDapps developed  | Yes         | Yes                   | Yes           |
| for this Corda version can be compiled and run  |             |                       |               |
| on Corda Enterprise 4.0 nodes                   |             |                       |               |
+-------------------------------------------------+-------------+-----------------------+---------------+
| **Binary compatibility**, i.e. CorDapps         | Yes         | Yes                   | Yes           |
| compiled on this Corda version can be run on    |             |                       |               |
| Corda Enterprise 4.0 nodes                      |             |                       |               |
+-------------------------------------------------+-------------+-----------------------+---------------+
| **Network compatibility**, i.e., nodes running  | Yes         | Yes                   | Yes           |
| this Corda version can transact with Corda      |             |                       |               |
| Enterprise 4.0 nodes                            |             |                       |               |
+-------------------------------------------------+-------------+-----------------------+---------------+
| **RPC compatibility**, i.e, a client            | Yes         | Yes                   | No            |
| application developed for this Corda version    |             |                       |               |
| can interact via RPC with a CorDapp running on  |             |                       |               |
| an Corda Enterprise 4.0 node                    |             |                       |               |
+-------------------------------------------------+-------------+-----------------------+---------------+
| Samples and community apps from                 | Yes         | Yes                   | Yes           |
| https://github.com/corda/samples for this Corda |             |                       |               |
| version can be compiled and run on Corda        |             |                       |               |
| Enterprise 4.0 nodes                            |             |                       |               |
+-------------------------------------------------+-------------+-----------------------+---------------+

.. note::

   Compatibility guarantees apply to the latest minor release version of either distribution, including any subsequent patches.
   At the time of writing these are Enterprise release version 3.3 and Open Source release version 3.4.
   See :doc:`versioning` for further information.
