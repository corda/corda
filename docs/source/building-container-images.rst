=========================
Building container images
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

Below is an example directory layout and command to run your image with Docker.
Make sure to run ``touch persistence.mv.db`` befor starting the container,
otherwise a new directory will be created by Docker.

::

        .
        ├── additional-node-infos
        ├── certificates
        ├── config
        │   └── node.conf
        ├── network-parameters
        └── persistence.mv.db

.. sourcecode:: shell

        docker run --rm -it -v ${PWD}/certificates:/certificates \
                            -v ${PWD}/config:/config \
                            -v ${PWD}/network-parameters:/network-parameters \
                            -v ${PWD}/persistence.mv.db:/persistence.mv.db \
                            -v ${PWD}/additional-node-infos:/additional-node-infos \
                            <registry>/<image>:<tag>
