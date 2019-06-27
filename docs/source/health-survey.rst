.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Health Survey Tool
==================

The Health Survey Tool is a command line utility that can be used to collect information about a node,
which can be used by the R3 support Team as an aid to diagnose support issues. It works by scanning through a provided
node base directory and archiving some of the important files:

* A sanitised ``node.conf`` with passwords removed.
* Version and environment information - Java version, Corda version, OS version.
* Network information - DNS lookup to databases, network map, doorman and external addresses.
* Network parameters file.
* List of installed CorDapps - names, file sizes and checksums.
* List of drivers - jdbc drivers stored in the ``drivers/`` folder.
* Node information files including additional-node-infos
* ``Logs`` based on confirmation prompt - if declined, they are completely skipped.

.. |jar_name| replace:: corda-tools-health-survey-|version|.jar

Running
-------

.. parsed-literal::

    > java -jar |jar_name| --base-directory DIRECTORY [--node-configuration DIRECTORY]
..

Usage:

   *  ``-c``, ``--node-configuration`` <arg>:   Path to the Corda node configuration file, optional
   *  ``-d``, ``--base-directory`` <arg>:       Path to the Corda node base directory

Running the tool with no arguments assumes that the base-directory argument is the current working directory.

Output
------

The tool generates the archive of the collected files in the same directory it is ran in. The names are in the format: ``report-date-time.zip``

.. image:: resources/health-survey/health-survey-photo.png
   :scale: 100 %
