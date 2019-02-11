===================================================
Obtaining and Installing the Performance Test Suite
===================================================

As a registered user of Corda Enterprise, you can get the performance test suite as a zip file from the same location where the Corda
Enterprise artifacts are available. Look for a file called ``jmeter-corda-<version>-testsuite.zip``.

File Contents
=============

The performance test suite comes as a zip file containing the following files:

``jmeter-corda-<version>-capsule.jar``
    The JAR file that contains the wrapped JMeter code to to drive performance tests. This is a fat jar that contains all the required
    dependencies to run the JMeter application. It will be referred to as ``jmeter-corda.jar`` in the rest of this documentation

``perftestcordapp-<version>.jar``
    The performance test CorDapp used in for the built-in samplers and the included sample test plans. This needs to
    be deployed to any node of the system under test if these test plans will be used.

Sample Testplans
    A number of test plan JMX files **TODO** describe test plans https://r3-cev.atlassian.net/browse/ENT-2644

Sample ``jmeter.properties``
    An example of the ``jmeter.properties`` file used to configure JMeter. If you need a custom configuration, it is
    recommended to base it on this file.

Installation
============

Client Installation
-------------------

Simply create a working directory for JMeter Corda on the client machine and unzip the performance test suite to this
directory. This app requires an Oracle JRE version 1.8 build 172 or later. After unpacking,
you should immediately be able to run it from a shell by typing ``java -jar jmeter-corda.jar``. Please refer to
:doc:`running-jmeter-corda` for details and command line options.

.. _jmeter_server:

Installing JMeter server
------------------------

The same JAR file used for running the client can be run as a server as well by starting it with the ``-s`` flag as part
of the JMeter arguments. It can be installed by simply copying the JAR file to the server and having an adequate JRE
installed.

If SSH tunneling is in use, the server will also need access to the same RMI port mappings file used on the client side.
Also, if the client side uses a customised JMeter properties file that differs from the default one baked into the JMeter
JAR file by more than just the
list of remote host names from, this needs to be used on the server side as well. See :doc:`running-jmeter-corda`
for details on the command line options required. A typical command line might look like this::

    java -jar <path to jmeter-corda jar> -XjmeterProperties <path to properties file> -XserverRmiMappings <path to RMI mappings file> -- -s

If you want to use JMeter Corda remotely, it is suggested to install the JMeter server as a system service on the servers
it is required to run on. Assuming a linux system with a systemd based run control, the service to install would look
something like this::

    [Unit]
    Description=Jmeter Corda
    Requires=network.target

    [Service]
    Type=simple
    User=<jmeter Corda user>
    WorkingDirectory=<jmeter deploy directory>
    ExecStart=/usr/bin/java -Xmx512m -jar <JMeter deploy directory>/jmeter-corda.jar -XjmeterProperties <path to properties file> -XserverRmiMappings <path to RMI mappings file> -- -s
    Restart=on-failure

    [Install]
    WantedBy=multi-user.target

