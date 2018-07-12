=========================
Building Container Images
=========================

To build a container image of Corda you can use the Jib gradle tasks. See the `documentation of the Jib gradle plugin <https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin>`_ for details.

Building the image
==================

To build an image locally you can use the following command. Note that you do not require Docker.

.. sourcecode:: shell

        ./gradlew node:jib --image <registry>/<image>:<tag>

If you prefer building to a Docker deamon you can use

.. sourcecode:: shell

        ./gradlew node:jibDockerBuild --image <registry>/<image>:<tag>

Running the image
=================

The Corda application expects its config file in ``/config/node.conf``, make
sure you mount the config file to that location. You might also want to mount
``/credentials`` and ``/persistence.mv.db`` (if you're using H2) in order to
preserve the credentials and node data between container restarts.

The JVM options are currently hardcoded in ``node/build.gradle`` in the
``jib.container`` section.
