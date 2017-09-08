Gradle Plugins for Cordapps
===========================

The projects at this level of the project are gradle plugins for cordapps and are published to Maven Local with
the rest of the Corda libraries.

.. note::

     Some of the plugins here are duplicated with the ones in buildSrc. While the duplication is unwanted any
     currently known solution (such as publishing from buildSrc or setting up a separate project/repo) would
     introduce a two step build which is less convenient.

Version number
--------------

To modify the version number edit constants.properties in root dir

Installing
----------

If you need to bootstrap the corda repository you can install these plugins with

.. code-block:: text

    cd publish-utils
    ../../gradlew -u install
    cd ../
    ../gradlew install

