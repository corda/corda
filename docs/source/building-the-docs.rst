Building the documentation
==========================

The documentation is under the ``docs`` folder, and is written in reStructuredText format. Documentation in HTML format
is pre-generated, as well as code documentation, and this can be done automatically via a provided script.

Requirements
------------

In order to build the documentation you will need:

1. Docker version 17 and above
2. (OS X and Windows Only) The drive which hosts your corda checkout must be shared with docker.

Build
-----

Once the requirements are installed, you can automatically build the HTML format user documentation, PDF, and
the API documentation by running the following script:

.. sourcecode:: shell

    // On Windows
    gradlew buildDocs

    // On Mac and Linux
    ./gradlew buildDocs