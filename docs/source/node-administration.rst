Node administration
===================

When a node is running, it exposes an RPC interface that lets you monitor it,
you can upload and download attachments, access a REST API and so on. A bundled
Jetty web server exposes the same interface over HTTP.

Logging
-------

By default the node log files are stored to the ``logs`` subdirectory of the working directory and are rotated from time
to time. You can have logging printed to the console as well by passing the ``--log-to-console`` command line flag.
The default logging level is ``INFO`` which can be adjusted by the ``--logging-level`` command line argument. For more
custom logging, the logger settings can be completely overridden with a `Log4j 2 <https://logging.apache.org/log4j/2.x>`_
configuration file assigned to the ``log4j.configurationFile`` system property.

Database access
---------------

The node exposes its internal database over a socket which can be browsed using any tool that can use JDBC drivers.
The JDBC URL is printed during node startup to the log and will typically look like this:

     ``jdbc:h2:tcp://192.168.0.31:31339/node``

The username and password can be altered in the :doc:`corda-configuration-file` but default to username "sa" and a blank
password.

Any database browsing tool that supports JDBC can be used, but if you have IntelliJ Ultimate edition then there is
a tool integrated with your IDE. Just open the database window and add an H2 data source with the above details.
You will now be able to browse the tables and row data within them.

Monitoring your node
--------------------

Like most Java servers, the node exports various useful metrics and management operations via the industry-standard
`JMX infrastructure <https://en.wikipedia.org/wiki/Java_Management_Extensions>`_. JMX is a standard API
for registering so-called *MBeans* ... objects whose properties and methods are intended for server management. It does
not require any particular network protocol for export. So this data can be exported from the node in various ways:
some monitoring systems provide a "Java Agent", which is essentially a JVM plugin that finds all the MBeans and sends
them out to a statistics collector over the network. For those systems, follow the instructions provided by the vendor.

.. warning:: As of Corda M11, Java serialisation in Corda node has been restricted, meaning mbean access via JMX port
will no longer work. Please use java agents instead.

Sometimes though, you just want raw access to the data and operations itself. So nodes export them over HTTP on the
``/monitoring/json`` HTTP endpoint, using a program called `Jolokia <https://jolokia.org/>`_. Jolokia defines the JSON
and REST formats for accessing MBeans, and provides client libraries to work with that protocol as well.

Here are a few ways to build dashboards and extract monitoring data for a node:

* `JMX2Graphite <https://github.com/logzio/jmx2graphite>`_ is a tool that can be pointed to /monitoring/json and will
  scrape the statistics found there, then insert them into the Graphite monitoring tool on a regular basis. It runs
  in Docker and can be started with a single command.
* `JMXTrans <https://github.com/jmxtrans/jmxtrans>`_ is another tool for Graphite, this time, it's got its own agent
  (JVM plugin) which reads a custom config file and exports only the named data. It's more configurable than
  JMX2Graphite and doesn't require a separate process, as the JVM will write directly to Graphite.
* Cloud metrics services like New Relic also understand JMX, typically, by providing their own agent that uploads the
  data to their service on a regular schedule.
* `Telegraf <https://github.com/influxdata/telegraf>`_ is a tool to collecting, processing, aggregating, and writing
  metrics. It can bridge different data input to different output using their plugin system, for example, Telegraf can
  be configured to collect data from Jolokia and write to DataDog web api.

Memory usage and tuning
-----------------------

All garbage collected programs can run faster if you give them more memory, as they need to collect less
frequently. As a default JVM will happily consume all the memory on your system if you let it, Corda is
configured with a relatively small 200mb Java heap by default. When other overheads are added, this yields
a total memory usage of about 500mb for a node (the overheads come from things like compiled code, metadata,
off-heap buffers, thread stacks, etc).

If you want to make your node go faster and profiling suggests excessive GC overhead is the cause, or if your
node is running out of memory, you can give it more by running the node like this:

``java -Xmx1024m -jar corda.jar``

The example command above would give a 1 gigabyte Java heap.

.. note:: Unfortunately the JVM does not let you limit the total memory usage of Java program, just the heap size.